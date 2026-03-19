package com.springapp.proiectcrm.controller;


import com.springapp.proiectcrm.dto.LoginRequest;
import com.springapp.proiectcrm.dto.LoginResponse;
import com.springapp.proiectcrm.model.User;
import com.springapp.proiectcrm.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request,
                               HttpServletRequest httpReq,
                               HttpServletResponse httpRes) {

        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        // session fixation protection:
        // - dacă există deja sesiune, îi rotești ID-ul
        // - dacă nu există, creezi una nouă
        if (httpReq.getSession(false) != null) {
            httpReq.changeSessionId();
        } else {
            httpReq.getSession(true);
        }

        securityContextRepository.saveContext(context, httpReq, httpRes);

        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

        return toLoginResponse(user);
    }

    @GetMapping("/me")
    public LoginResponse me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

        return toLoginResponse(user);
    }

    private LoginResponse toLoginResponse(User user) {
        String roleName = (user.getRole() != null) ? user.getRole().getRoleName() : null;
        return new LoginResponse(user.getIdUser(), roleName, user.getFirstName(), user.getLastName());
    }
}