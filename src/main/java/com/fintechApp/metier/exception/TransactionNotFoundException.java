package com.fintechApp.metier.exception;

/**
 * Levée quand la transaction ciblée n'existe pas. Hérite de
 * RessourceIntrouvableException (comme CompteIntrouvableException,
 * UtilisateurIntrouvableException...) plutôt que de RuntimeException nue :
 * l'ancienne version, avec seulement @ResponseStatus(HttpStatus.NOT_FOUND),
 * était en réalité interceptée par le handler générique
 * @ExceptionHandler(Exception.class) de GlobalExceptionHandler (qui
 * s'applique à toute exception non prise en charge par un handler plus
 * spécifique dans la même classe) et renvoyait donc 500 au lieu de 404.
 */
public class TransactionNotFoundException extends RessourceIntrouvableException {

    public TransactionNotFoundException(String message) {
        super("TRANSACTION_INTROUVABLE", message);
    }
}
