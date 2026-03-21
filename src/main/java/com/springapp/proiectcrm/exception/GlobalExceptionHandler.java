package com.springapp.proiectcrm.exception;

import com.springapp.proiectcrm.dto.ApiErrorResponse;
import com.springapp.proiectcrm.logging.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Handler global pentru excepții — interceptează toate excepțiile nemenaj
 * aruncate din controllere și le transformă în răspunsuri JSON standardizate.
 *
 * Comportament de logging:
 *   BusinessException  → log.warn  (eroare așteptată de business, nu critică)
 *   ValidationException → log.warn (input greșit de la client)
 *   DataIntegrityViolation → log.warn (constraint BD — de obicei duplicate)
 *   Exception generic  → log.error cu stack trace (eroare neașteptată, critică)
 *
 * De ce WARN și nu ERROR pentru BusinessException?
 *   BusinessException reprezintă comportamente valide ale sistemului:
 *   "grupa e plină", "emailul există deja", "copilul e deja dezactivat".
 *   Acestea nu indică un bug — sunt fluxuri normale gestionate incorect de client.
 *   ERROR este rezervat pentru situații neașteptate care necesită intervenție.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ══════════════════════════════════════════════════════════════════════════
    // BusinessException — erori de business așteptate
    // ══════════════════════════════════════════════════════════════════════════

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request
    ) {
        ErrorCode code = ex.getErrorCode();

        HttpStatus status = switch (code) {
            case EMAIL_ALREADY_EXISTS,
                 CHILD_ALREADY_ENROLLED,
                 GROUP_FULL               -> HttpStatus.CONFLICT;

            case GROUP_ENDED,
                 GROUP_INACTIVE,
                 CHILD_HAS_INACTIVE_ENROLLMENT,
                 BUSINESS_RULE_VIOLATION,
                 VALIDATION_ERROR,
                 INVALID_ENROLLMENT_REQUEST,
                 TOO_MANY_CHILDREN_IN_REQUEST,
                 DUPLICATE_CHILD_IN_REQUEST -> HttpStatus.BAD_REQUEST;

            case GROUP_NOT_FOUND,
                 CHILD_NOT_FOUND,
                 PARENT_NOT_FOUND,
                 TEACHER_NOT_FOUND,
                 SESSION_NOT_FOUND         -> HttpStatus.NOT_FOUND;

            case INVALID_CREDENTIALS       -> HttpStatus.UNAUTHORIZED;
            case ACCESS_DENIED,
                 USER_NOT_PARENT_ROLE      -> HttpStatus.FORBIDDEN;

            default                        -> HttpStatus.BAD_REQUEST;
        };

        // WARN: eroare de business așteptată — nu critică, dar relevantă pentru audit
        // Includem codul de eroare și mesajul pentru a înțelege contextul fără să deschidem BD
        log.warn("BUSINESS_EXCEPTION code={} status={} message=\"{}\" path={}",
                code.name(), status.value(),
                LogSanitizer.sanitize(ex.getMessage()),
                LogSanitizer.sanitize(request.getRequestURI()));

        ApiErrorResponse body = ApiErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                code.name()
        );
        return new ResponseEntity<>(body, status);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Validare input (@Valid, @RequestBody)
    // ══════════════════════════════════════════════════════════════════════════

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        String message = "Date invalide în cerere.";
        FieldError firstFieldError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        if (firstFieldError != null && firstFieldError.getDefaultMessage() != null) {
            message = firstFieldError.getDefaultMessage();
        }

        // WARN: client a trimis date invalide — comportament așteptat, nu bug
        log.warn("VALIDATION_FAILED path={} field={} message=\"{}\"",
                LogSanitizer.sanitize(request.getRequestURI()),
                firstFieldError != null ? firstFieldError.getField() : "unknown",
                LogSanitizer.sanitize(message));

        ApiErrorResponse body = ApiErrorResponse.of(
                status.value(), status.getReasonPhrase(), message,
                request.getRequestURI(), ErrorCode.VALIDATION_ERROR.name()
        );
        return new ResponseEntity<>(body, status);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // IllegalArgumentException
    // ══════════════════════════════════════════════════════════════════════════

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        // WARN: argument invalid — de obicei ID inexistent trimis de client
        log.warn("ILLEGAL_ARGUMENT path={} message=\"{}\"",
                LogSanitizer.sanitize(request.getRequestURI()),
                LogSanitizer.sanitize(ex.getMessage()));

        HttpStatus status = HttpStatus.BAD_REQUEST;
        ApiErrorResponse body = ApiErrorResponse.of(
                status.value(), status.getReasonPhrase(), ex.getMessage(),
                request.getRequestURI(), ErrorCode.BUSINESS_RULE_VIOLATION.name()
        );
        return new ResponseEntity<>(body, status);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Parametru lipsă din request
    // ══════════════════════════════════════════════════════════════════════════

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        String message = "Lipsește parametrul obligatoriu '" + ex.getParameterName() + "'.";

        log.warn("MISSING_PARAM path={} param={}",
                LogSanitizer.sanitize(request.getRequestURI()),
                LogSanitizer.sanitize(ex.getParameterName()));

        HttpStatus status = HttpStatus.BAD_REQUEST;
        ApiErrorResponse body = ApiErrorResponse.of(
                status.value(), status.getReasonPhrase(), message,
                request.getRequestURI(), "MISSING_PARAMETER"
        );
        return new ResponseEntity<>(body, status);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DataIntegrityViolationException — constraint BD violat
    // ══════════════════════════════════════════════════════════════════════════

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.CONFLICT;

        String rootMsg = ex.getMostSpecificCause().getMessage();
        if (rootMsg == null) rootMsg = "";

        String message;
        String errorCode;

        // Detectăm care constraint a fost violat pentru un mesaj relevant utilizatorului
        if (rootMsg.contains("uq_child_group") || rootMsg.contains("child_group")) {
            message   = "Copilul este deja înscris în această grupă.";
            errorCode = ErrorCode.CHILD_ALREADY_ENROLLED.name();
        } else if (rootMsg.contains("email") || rootMsg.contains("uq_user_email")) {
            message   = "Există deja un cont cu acest email. Te rugăm să te autentifici și să continui din contul existent.";
            errorCode = ErrorCode.EMAIL_ALREADY_EXISTS.name();
        } else {
            message   = "Operațiunea nu poate fi efectuată: există deja o înregistrare cu aceste date.";
            errorCode = "DUPLICATE_ENTRY";
        }

        // WARN: constraint violation — relevant pentru debugging dar nu critic
        // Nu loghăm rootMsg complet deoarece poate conține date sensibile din BD
        log.warn("DATA_INTEGRITY_VIOLATION path={} constraint={} code={}",
                request.getRequestURI(), errorCode, status.value());

        ApiErrorResponse body = ApiErrorResponse.of(
                status.value(), status.getReasonPhrase(), message,
                request.getRequestURI(), errorCode
        );
        return new ResponseEntity<>(body, status);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Exception generic — erori neașteptate (BUG-uri)
    // ══════════════════════════════════════════════════════════════════════════

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(
            Exception ex,
            HttpServletRequest request
    ) {
        // ERROR cu stack trace: aceasta este o eroare neașteptată care necesită investigare
        // Apare în error.log (filtrat WARN+) și app.log
        // ex.getMessage() poate fi null pentru unele excepții (ex: NullPointerException)
        log.error("UNHANDLED_EXCEPTION path={} exceptionClass={} message=\"{}\"",
                LogSanitizer.sanitize(request.getRequestURI()),
                ex.getClass().getSimpleName(),
                LogSanitizer.sanitize(ex.getMessage()),
                ex);  // ultimul argument `ex` face Logback să includă stack trace-ul complet

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ApiErrorResponse body = ApiErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                "A apărut o eroare internă. Te rugăm să încerci din nou.",
                request.getRequestURI(),
                "INTERNAL_ERROR"
        );
        return new ResponseEntity<>(body, status);
    }

    // Adaugă acest handler — lipsea complet
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {

        // WARN în loc de ERROR — e o eroare de client (method greșit), nu bug de server
        log.warn("METHOD_NOT_SUPPORTED method={} path={} supported={}",
                LogSanitizer.sanitize(ex.getMethod()),
                LogSanitizer.sanitize(request.getRequestURI()),
                ex.getSupportedHttpMethods());

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiErrorResponse.of(
                        HttpStatus.METHOD_NOT_ALLOWED.value(),
                        "Method Not Allowed",
                        "Metoda HTTP " + ex.getMethod() + " nu este suportată pe această rută.",
                        request.getRequestURI(),
                        "METHOD_NOT_SUPPORTED"
                ));
    }

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        log.warn("BAD_CREDENTIALS path={}", LogSanitizer.sanitize(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of(401, "Unauthorized",
                        "Email sau parolă incorecte.", request.getRequestURI(), "BAD_CREDENTIALS"));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotWritableException.class)
    public ResponseEntity<Void> handleMessageNotWritable(
            HttpMessageNotWritableException ex, HttpServletRequest request) {
        // Ignorăm silențios — apare pe /ws/** când SockJS folosește xhr_streaming
        // Content-Type: application/javascript nu suportă JSON → nu putem scrie răspuns
        if (request.getRequestURI().startsWith("/ws/")) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        log.error("MESSAGE_NOT_WRITABLE path={} error=\"{}\"",
                LogSanitizer.sanitize(request.getRequestURI()),
                LogSanitizer.sanitize(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
