package com.fintechApp.metier.exception;

public class UtilisateurNonActifException extends RegleMetierException {
    public UtilisateurNonActifException(Integer idUtilisateur) {
        super("UTILISATEUR_NON_ACTIF", "L'utilisateur " + idUtilisateur + " n'est pas actif", 403);
    }
}
