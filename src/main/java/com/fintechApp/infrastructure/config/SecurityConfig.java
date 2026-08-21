package com.fintechApp.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fintechApp.infrastructure.security.jwt.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Expose l'AuthenticationManager pour qu'il puisse être injecté dans
     * UtilisateurController (endpoint /connexion). Sans ce bean explicite,
     * Spring Security 6 ne le publie pas automatiquement et l'injection
     * échoue au démarrage.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()) // Désactivation du CSRF car nous sommes en stateless
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(formLogin -> formLogin.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Pas de session
            .authorizeHttpRequests(auth -> auth
                // Routes accessibles sans token : inscription, connexion, validation du code reçu par email
                .requestMatchers(
                    "/api/v1/utilisateurs/creer", 
                    "/api/v1/utilisateurs/connexion", 
                    "/api/v1/utilisateurs/valider", 
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/swagger-ui.html",
                    "/v3/api-docs.yaml"
                ).permitAll()
                .anyRequest().authenticated() // Toutes les autres routes nécessitent un token JWT valide
            );

        // Filtre JWT réactivé : sans lui, aucune requête authentifiée par
        // token ne pouvait jamais peupler le SecurityContext, et toutes les
        // routes protégées étaient de facto inaccessibles.
        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationFilter authenticationJwtTokenFilter() {
        return new JwtAuthenticationFilter();
    }
}
