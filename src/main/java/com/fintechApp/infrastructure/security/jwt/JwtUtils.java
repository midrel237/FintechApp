package com.fintechApp.infrastructure.security.jwt;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;


@Component
public class JwtUtils {
    @Value("${app.jwtSecret}") // Récupère une clé secrète définie dans application.yaml
    private String jwtSecret;

    @Value("${app.jwtExpirationMs}") // Durée de validité du token
    private int jwtExpirationMs;

    // cle calculee une seule fois reutilisee partout
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    // ÉLÉMENT CLÉ : Génération du token après succès de l'authentification
    public String generateToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();

        return Jwts.builder()
                .setSubject(userPrincipal.getUsername()) // Définit le sujet (nom d'utilisateur)
                .setIssuedAt(new Date()) // Date de création
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs)) // Date d'expiration
                .signWith(getSigningKey(), SignatureAlgorithm.HS512) // Signature pour garantir l'intégrité
                .compact();
    }

    // ÉLÉMENT CLÉ : Extraction du nom d'utilisateur depuis le token
    public String getUserNameFromJwtToken(String token) {
        return Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token).getBody().getSubject();
    }

    // ÉLÉMENT CLÉ : Validation de l'intégrité et de l'expiration du token
    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(authToken);
            return true;
        } catch (ExpiredJwtException | MalformedJwtException | UnsupportedJwtException | SignatureException | IllegalArgumentException e) {
            // Le token peut être expiré, mal formé ou la signature peut être invalide
            return false;
        }
    }
}

