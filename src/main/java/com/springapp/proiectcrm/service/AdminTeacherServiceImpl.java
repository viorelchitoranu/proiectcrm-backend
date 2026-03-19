package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.TeacherActiveRequest;
import com.springapp.proiectcrm.dto.TeacherCreateRequest;
import com.springapp.proiectcrm.dto.TeacherPasswordUpdateRequest;
import com.springapp.proiectcrm.dto.TeacherResponse;
import com.springapp.proiectcrm.model.Role;
import com.springapp.proiectcrm.model.SessionStatus;
import com.springapp.proiectcrm.model.TeachingRole;
import com.springapp.proiectcrm.model.User;
import com.springapp.proiectcrm.repository.RoleRepository;
import com.springapp.proiectcrm.repository.TeacherSessionRepository;
import com.springapp.proiectcrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminTeacherServiceImpl implements AdminTeacherService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder; // Encoder
    private final EmailService emailService;
    private final TeacherSessionRepository teacherSessionRepository;

    private static final String TEACHER_ROLE_NAME = "TEACHER"; //

    // mapare entity -> DTO.
    private TeacherResponse mapTeacher(User teacher) {
        Boolean active = teacher.getActive();
        if (active == null) active = Boolean.TRUE;
        return new TeacherResponse(
                teacher.getIdUser(),
                teacher.getFirstName(),
                teacher.getLastName(),
                teacher.getEmail(),
                teacher.getPhone(),
                active,
                teacher.getCreatedAt()
        );
    }

    // Helper pentru rol TEACHER
    private Role getTeacherRole() {
        return roleRepository.findByRoleName(TEACHER_ROLE_NAME)
                .orElseThrow(() -> new IllegalStateException("Rolul TEACHER nu există în baza de date."));
    }
    // Helper: verificăm dacă user e teacher.
    private void assertTeacher(User user) {
        if (user.getRole() == null || user.getRole().getRoleName() == null) {
            throw new IllegalStateException("User-ul nu are rol setat.");
        }
        if (!TEACHER_ROLE_NAME.equalsIgnoreCase(user.getRole().getRoleName())) {
            throw new IllegalStateException("User-ul nu are rol TEACHER.");
        }
    }
    // Creare profesor
    @Override
    public TeacherResponse createTeacher(TeacherCreateRequest request) {

        userRepository.findByEmail(request.getEmail())
                .ifPresent(u -> { throw new IllegalStateException("Există deja un user cu acest email."); });

        Role teacherRole = getTeacherRole();

        // Construiesc teacher in baza de date
        User teacher = new User();
        teacher.setFirstName(request.getFirstName());
        teacher.setLastName(request.getLastName());
        teacher.setEmail(request.getEmail());
        teacher.setPhone(request.getPhone());
        teacher.setAddress(request.getAddress());
        teacher.setRole(teacherRole);
        teacher.setCreatedAt(LocalDate.now());
        teacher.setActive(Boolean.TRUE);

        String rawPassword = request.getPassword(); // Parola în clar
        teacher.setPassword(passwordEncoder.encode(rawPassword));
        //teacher.setPassword(rawPassword);

        // Salvare teacher
        User saved = userRepository.save(teacher);

        // Se incearca trimitere email cu credentiale
        try {
            emailService.sendTeacherCredentials(saved, rawPassword);
        } catch (Exception ex) {
            System.err.println("Eroare la trimiterea email-ului de credențiale: " + ex.getMessage());
        }

        // Returnare DTO pentru raspuns
        return mapTeacher(saved);
    }

    // Listare profesori
    @Override
    public List<TeacherResponse> getAllTeachers(Boolean active) {

        Role teacherRole = getTeacherRole();
        List<User> teachers = userRepository.findByRole(teacherRole);

        return teachers.stream()
                .filter(t -> {
                    if (active == null) return true;
                    Boolean a = t.getActive();
                    if (a == null) a = Boolean.TRUE;
                    return a.equals(active);
                })
                .map(this::mapTeacher) // referinta metoda
                .toList();
    }
    // Reset parola
    @Override
    public TeacherResponse updateTeacherPassword(int teacherId, TeacherPasswordUpdateRequest request) {

        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        assertTeacher(teacher); // Verificcare rol TEACHER.

        String rawNewPassword = request.getNewPassword();
        teacher.setPassword(passwordEncoder.encode(rawNewPassword));
        //teacher.setPassword(rawNewPassword);

        User saved = userRepository.save(teacher);
        // Email reset
        try {
            emailService.sendTeacherPasswordReset(saved, rawNewPassword);
        } catch (Exception ex) {
            System.err.println("Eroare la trimiterea email-ului de resetare parolă: " + ex.getMessage());
        }

        return mapTeacher(saved);
    }


    /*
    Activare sau dezactivare cont profesor. La dezactivare există o verificare critică de business:
•	Dacă nextActive=false → caută în TeacherSessionRepository dacă există sesiuni viitoare (sessionDate >= azi) cu status PLANNED și teachingRole=MAIN pentru acest profesor
•	Dacă există sesiuni viitoare → aruncă ResponseStatusException(409 CONFLICT) cu mesaj de reasignare
•	Dacă nu există → setează active=false și salvează
Validare request: Dacă request sau request.active e null → 400 BAD_REQUEST
     */
    // Activ/inactiv
    @Override
    public TeacherResponse setTeacherActive(int teacherId, TeacherActiveRequest request) {

        if (request == null || request.getActive() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Câmpul `active` este obligatoriu."); // 400
        }

        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        assertTeacher(teacher); // Verificare rol TEACHER.

        boolean nextActive = request.getActive();

        if (!nextActive) {
            boolean hasFuturePlanned = teacherSessionRepository // Verificare sesiuni viitoare PLANNED.
                    .existsByTeacherAndTeachingRoleAndSession_SessionDateGreaterThanEqualAndSession_SessionStatus(
                            teacher,
                            TeachingRole.MAIN,
                            LocalDate.now(),
                            SessionStatus.PLANNED
                    );

            if (hasFuturePlanned) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, // 409
                        "Nu poți dezactiva profesorul: are sesiuni viitoare (PLANNED). Reasignează grupele și încearcă din nou."
                );
            }
        }

        teacher.setActive(nextActive);
        User saved = userRepository.save(teacher);

        return mapTeacher(saved);
    }
}
