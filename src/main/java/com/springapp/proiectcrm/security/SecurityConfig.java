package com.springapp.proiectcrm.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configurarea principală Spring Security.
 *
 * Modificări față de versiunea anterioară (pentru Modulul 5 — Message Board):
 *
 * 1. Adăugat: .requestMatchers("/ws/**").authenticated()
 *    SockJS (fallback WebSocket) face cereri HTTP la /ws/info și /ws/{id}/...
 *    pentru negocierea conexiunii. Aceste cereri transportă JSESSIONID →
 *    Spring Security le poate autentifica. Fără această regulă, SockJS ar
 *    putea fi accesat anonim sau blocat de regula anyRequest().authenticated().
 *
 * 2. Adăugat: .requestMatchers("/api/board/**").authenticated()
 *    Endpoint-ele REST ale forumului (history, delete) necesită autentificare.
 *    Nu restricționăm la un rol specific — verificarea rolului se face per endpoint
 *    (adminul poate șterge cu @PreAuthorize, istoricul e pentru toți autentificații).
 *
 * 3. Adăugat: @EnableMethodSecurity
 *    Necesar pentru ca @PreAuthorize pe deletePost() să funcționeze.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // NOU: activează @PreAuthorize pe metode individuale
public class SecurityConfig {

    // FIX: CORS origins din properties, nu hardcodat
    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityContextRepository securityContextRepository) throws Exception {
        http
                // ── CSRF: activat cu CookieCsrfTokenRepository ────────────────────────
                // Backend trimite cookie XSRF-TOKEN (HttpOnly=false) → frontend îl citește
                // și îl trimite în header-ul X-XSRF-TOKEN la fiecare request non-GET.
                // Ignorăm CSRF pentru:
                //   /api/auth/**  → login/logout nu necesită token (sesiunea nu există încă)
                //   /ws/**        → SockJS gestionează propria autentificare prin JSESSIONID
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/api/auth/**", "/ws/**")
                )
                .cors(Customizer.withDefaults())
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/enrollments/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/courses/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/groups/**").permitAll()

                        // ── NOU: WebSocket SockJS negociere ────────────────────────────────
                        // SockJS face cereri HTTP la /ws/info, /ws/{server}/{session}/...
                        // Trebuie autentificate — cookie JSESSIONID e trimis automat de browser.
                        // ATENTIE: dacă lăsăm /ws/** la anyRequest() fără regulă explicită,
                        // ordinea regulilor poate cauza comportament neașteptat.
                        .requestMatchers("/ws/**").authenticated()

                        // ── NOU: REST endpoints forum ──────────────────────────────────────
                        // History: orice utilizator autentificat (rol verificat în service)
                        // Delete: @PreAuthorize("hasRole('ADMIN')") în controller
                        .requestMatchers("/api/board/**").authenticated()

                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/teacher/**").hasRole("TEACHER")
                        .requestMatchers("/api/parent/**").hasRole("PARENT")
                        .anyRequest().authenticated()
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((req, res, auth) ->
                                res.setStatus(HttpServletResponse.SC_NO_CONTENT))
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) ->
                                res.sendError(401, "Unauthorized"))
                        .accessDeniedHandler((req, res, ex) ->
                                res.sendError(403, "Forbidden"))
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();

        // FIX: origins din @Value, suportă multiple origini separate prin virgulă
        cfg.setAllowedOrigins(Arrays.asList(allowedOrigins));
        cfg.setAllowCredentials(true);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
