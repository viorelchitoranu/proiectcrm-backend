package com.springapp.proiectcrm.security;

import com.springapp.proiectcrm.logging.MdcFilter;
import com.springapp.proiectcrm.model.User;
import com.springapp.proiectcrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Serviciu de autentificare — Spring Security apelează loadUserByUsername()
 * la fiecare tentativă de login cu email + parolă.
 *
 * Fluxul de autentificare:
 *   1. AuthController primește POST /api/auth/login cu email + parolă
 *   2. Spring Security apelează loadUserByUsername(email)
 *   3. Dacă utilizatorul există și parola e corectă → sesiune creată
 *   4. Dacă utilizatorul nu există sau parola e greșită → 401
 *
 * Ce loghează această clasă:
 *   - Login reușit         → INFO  (cine s-a autentificat)
 *   - Email inexistent     → WARN  (tentativă cu email necunoscut)
 *   - Cont dezactivat      → WARN  (cont valid dar inactiv)
 *   - Rol invalid/lipsă    → WARN  (problemă de configurare în BD)
 *
 * De ce nu loghăm "parolă greșită" separat?
 *   loadUserByUsername() nu vede parola — Spring Security compară parola
 *   după ce această metodă returnează UserDetails. Dacă parola e greșită,
 *   Spring Security aruncă BadCredentialsException fără să treacă prin acest serviciu.
 *
 * GDPR: emailul este mascat în log via MdcFilter.maskEmail()
 *   ionescu@gmail.com → ion***@***.com
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DbUserDetailsService implements UserDetailsService {

    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "TEACHER", "PARENT");

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        // ── Căutare utilizator în BD ──────────────────────────────────────────
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    // WARN: email inexistent — poate fi o greșeală sau o tentativă de brute-force
                    // Mascăm emailul în log pentru GDPR
                    log.warn("AUTH_UNKNOWN_EMAIL email={}", MdcFilter.maskEmail(email));
                    return new UsernameNotFoundException("User not found");
                });

        // ── Verificare cont activ ─────────────────────────────────────────────
        // null pe câmpul active = activ (conturile vechi fără câmpul active)
        boolean enabled = (u.getActive() == null) || Boolean.TRUE.equals(u.getActive());
        if (!enabled) {
            // WARN: utilizatorul există dar contul e dezactivat de admin
            log.warn("AUTH_ACCOUNT_DISABLED email={} userId={}",
                    MdcFilter.maskEmail(email), u.getIdUser());
            throw new DisabledException("User inactive");
        }

        // ── Verificare și validare rol ────────────────────────────────────────
        String roleName = (u.getRole() != null) ? u.getRole().getRoleName() : null;
        if (roleName == null || roleName.isBlank()) {
            // WARN: problemă de configurare în BD — utilizator fără rol
            log.warn("AUTH_NO_ROLE email={} userId={}", MdcFilter.maskEmail(email), u.getIdUser());
            throw new UsernameNotFoundException("User has no role");
        }

        roleName = roleName.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ROLES.contains(roleName)) {
            // WARN: rol necunoscut în BD (ex: typo, date corupte)
            log.warn("AUTH_INVALID_ROLE email={} userId={} role={}",
                    MdcFilter.maskEmail(email), u.getIdUser(), roleName);
            throw new UsernameNotFoundException("Invalid role");
        }

        // ── Autentificare reușită → INFO ──────────────────────────────────────
        // Loghăm la nivel de serviciu; Spring Security va crea sesiunea după ce
        // confirmă parola (BCrypt compare), dar noi loghăm accesul la date
        log.info("AUTH_SUCCESS email={} userId={} role={}",
                MdcFilter.maskEmail(email), u.getIdUser(), roleName);

        return org.springframework.security.core.userdetails.User
                .withUsername(u.getEmail())
                .password(u.getPassword())  // BCrypt hash din BD
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + roleName)))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }
}
