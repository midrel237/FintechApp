package com.fintechApp.metier.exception;

public class CompteSuspenduException extends RegleMetierException {
    public CompteSuspenduException(Integer idCompte) {
        super("COMPTE_SUSPENDU", "Le compte " + idCompte + " est suspendu", 409);
    }
}
