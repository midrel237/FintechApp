package com.fintechApp.presentation.exception;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import com.fintechApp.metier.exception.RegleMetierException;
import com.fintechApp.metier.exception.RessourceIntrouvableException;
import com.fintechApp.metier.exception.UtilisateurNonTrouveException;
import com.fintechApp.metier.exception.ValidationException;

/**
 * Traduit les exceptions métier en réponses JSON conformes au "Format
 * d'erreur unique" du contrat d'API (Partie 2), au lieu de laisser Spring
 * renvoyer une trace de pile brute en 500 par défaut.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UtilisateurNonTrouveException.class)
    public ResponseEntity<ErrorResponseDTO> gererUtilisateurNonTrouve(UtilisateurNonTrouveException ex, WebRequest req) {
        return construire("UTILISATEUR_INTROUVABLE", ex.getMessage(), HttpStatus.NOT_FOUND, req);
    }

    @ExceptionHandler(RessourceIntrouvableException.class)
    public ResponseEntity<ErrorResponseDTO> gererRessourceIntrouvable(RessourceIntrouvableException ex, WebRequest req) {
        return construire(ex.getCode(), ex.getMessage(), HttpStatus.NOT_FOUND, req);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponseDTO> gererValidation(ValidationException ex, WebRequest req) {
        return construire("VALIDATION_ERROR", ex.getMessage(), HttpStatus.BAD_REQUEST, req);
    }

    @ExceptionHandler(RegleMetierException.class)
    public ResponseEntity<ErrorResponseDTO> gererRegleMetier(RegleMetierException ex, WebRequest req) {
        HttpStatus status = HttpStatus.resolve(ex.getHttpStatus());
        if (status == null) {
            status = HttpStatus.UNPROCESSABLE_ENTITY;
        }
        return construire(ex.getCode(), ex.getMessage(), status, req);
    }

    // Identifiants incorrects à la connexion.
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponseDTO> gererIdentifiantsInvalides(BadCredentialsException ex, WebRequest req) {
        return construire("IDENTIFIANTS_INVALIDES", "Email ou mot de passe incorrect.", HttpStatus.UNAUTHORIZED, req);
    }

    // Compte non ACTIF (verrouillé) qui tente de se connecter (RG :
    // "un utilisateur ne peut interagir qu'après validation").
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponseDTO> gererCompteVerrouille(DisabledException ex, WebRequest req) {
        return construire("COMPTE_NON_VALIDE", "Ce compte n'a pas encore été validé.", HttpStatus.FORBIDDEN, req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponseDTO> gererAuthentificationEchouee(AuthenticationException ex, WebRequest req) {
        return construire("AUTHENTIFICATION_ECHOUEE", "Authentification impossible.", HttpStatus.UNAUTHORIZED, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> gererErreurInterne(Exception ex, WebRequest req) {
        return construire("ERREUR_INTERNE", "Une erreur interne est survenue.", HttpStatus.INTERNAL_SERVER_ERROR, req);
    }

    private ResponseEntity<ErrorResponseDTO> construire(String code, String message, HttpStatus status, WebRequest req) {
        String path = req.getDescription(false).replace("uri=", "");
        ErrorResponseDTO body = new ErrorResponseDTO(code, message, status.value(), path, List.of());
        return ResponseEntity.status(status).body(body);
    }
}
