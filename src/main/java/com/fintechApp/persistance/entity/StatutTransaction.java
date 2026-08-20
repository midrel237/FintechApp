package com.fintechApp.persistance.entity;

// Valeurs alignées EXACTEMENT sur la contrainte CHECK ck_transaction_statut de la
// base (fintech_db.sql) : CHECK (statut_t IN ('en_attente', 'validee', 'echouee', 'annulee', 'suspendue'))
// Comme pour StatutUtilisateur { verrouille, actif }, on nomme les constantes
// en minuscules : @Enumerated(EnumType.STRING) persiste le nom exact de la
// constante, donc un nom en MAJUSCULES casserait la contrainte CHECK en base.
public enum StatutTransaction { en_attente, validee, echouee, annulee, suspendue }
