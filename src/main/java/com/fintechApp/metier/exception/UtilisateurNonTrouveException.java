package com.fintechApp.metier.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND) //renvoyer automatiquement une erreur HTTP 404 au client
public class UtilisateurNonTrouveException extends RuntimeException {

    public UtilisateurNonTrouveException(String message) {
        super(message);
    }

}
