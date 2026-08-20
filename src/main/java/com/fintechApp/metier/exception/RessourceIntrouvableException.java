package com.fintechApp.metier.exception;

/**
 * Levée lorsque la ressource ciblée (transaction, compte, utilisateur...)
 * n'existe pas. Correspond au code HTTP 404 du contrat d'API.
 */
public class RessourceIntrouvableException extends RuntimeException {

    private final String code;

    public RessourceIntrouvableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
