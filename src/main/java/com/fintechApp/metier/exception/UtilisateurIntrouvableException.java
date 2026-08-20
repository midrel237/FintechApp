package com.fintechApp.metier.exception;

public class UtilisateurIntrouvableException extends RessourceIntrouvableException {
    public UtilisateurIntrouvableException(Integer idUtilisateur) {
        super("UTILISATEUR_INTROUVABLE", "Utilisateur introuvable : " + idUtilisateur);
    }
}
