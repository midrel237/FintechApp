package com.fintechApp.metier.exception;

import java.math.BigDecimal;

public class IncoherenceSoldeException extends RegleMetierException {
    public IncoherenceSoldeException(String numeroCompte) {
        super("INCOHERENCE_SOLDE", "Le solde du compte " + numeroCompte + " est incohérent", 409);
    }

    public IncoherenceSoldeException(Integer idCompte, BigDecimal solde, BigDecimal soldeRecalcule) {
        super("INCOHERENCE_SOLDE",
                "Le solde du compte " + idCompte + " est incohérent : solde actuel = " + solde
                        + ", solde recalculé = " + soldeRecalcule,
                409);
    }
}
