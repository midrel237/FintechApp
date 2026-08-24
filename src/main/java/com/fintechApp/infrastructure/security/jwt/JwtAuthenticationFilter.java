package com.fintechApp.infrastructure.security.jwt;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fintechApp.persistance.entity.Utilisateur;
import com.fintechApp.persistance.repository.UtilisateurRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Autowired private JwtUtils jwtUtils;
    @Autowired private UserDetailsService userDetailsService;
    // Nécessaire pour lire Utilisateur.tokenValideDepuis : UserDetails
    // (Spring Security) ne porte pas ce champ métier, on va donc le
    // rechercher directement en base pour chaque requête authentifiée.
    @Autowired private UtilisateurRepository utilisateurRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = parseJwt(request); // 1. Extraction du token depuis le header
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) { // 2. Validation (signature + expiration)
                String username = jwtUtils.getUserNameFromJwtToken(jwt); // 3. Extraction de l'utilisateur

                // 4. Rejet des tokens émis avant la dernière déconnexion.
                // Sans cette vérification, un JWT reste valide jusqu'à son
                // expiration naturelle même après un appel à /deconnexion,
                // puisqu'un JWT est par nature stateless (bug corrigé ici).
                // On ne pose PAS d'authentification -> la requête continue
                // sans identité, donc rejetée en aval par
                // anyRequest().authenticated() (SecurityConfig) sur toute
                // route protégée.
                if (!tokenRevoqueParDeconnexion(jwt, username)) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    // 5. Création de l'objet d'authentification pour le contexte Spring
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());

                    // 6. Stockage dans le SecurityContextHolder pour autoriser l'accès
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (UsernameNotFoundException e) { /* Erreur de validation */ }
        filterChain.doFilter(request, response);
    }

    private boolean tokenRevoqueParDeconnexion(String jwt, String username) {
        Utilisateur utilisateur = utilisateurRepository.findByEmail(username).orElse(null);
        if (utilisateur == null || utilisateur.getTokenValideDepuis() == null) {
            return false; // jamais déconnecté depuis l'émission d'un token -> rien à révoquer
        }

        LocalDateTime emisLe = jwtUtils.getIssuedAtFromJwtToken(jwt)
                .toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

        return !emisLe.isAfter(utilisateur.getTokenValideDepuis()); // émis avant/à la déconnexion -> révoqué
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7); // Récupère le token après "Bearer "
        }
        return null;
    }
}
