package com.fintechApp.metier.exception;

public class CompteIntrouvableException extends RessourceIntrouvableException {
    public CompteIntrouvableException(Integer idCompte) {
        super("COMPTE_INTROUVABLE", "Le compte " + idCompte + " est introuvable");
    }
}
