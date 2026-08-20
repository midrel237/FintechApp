    package com.fintechApp.metier.exception;

/**
 * Levée lorsqu'une requête est bien formée mais rejetée pour une raison
 * métier (règle de gestion violée). Le champ httpStatus permet à la couche
 * Présentation de choisir 403 (non autorisé), 409 (conflit d'état) ou 422
 * (règle métier), conformément au contrat d'API / format d'erreur unique.
 */
public class RegleMetierException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public RegleMetierException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}