package com.fintechApp.metier.exception;

public class CompteNonVideException extends RegleMetierException {
    public CompteNonVideException(Integer idCompte) {
        super("COMPTE_NON_VIDE", "Le compte " + idCompte + " n'est pas vide", 409);
    }
}
