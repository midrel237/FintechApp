package com.fintechApp.metier.exception;

import java.math.BigDecimal;

public class SoldeInsuffisantException extends RegleMetierException {
    public SoldeInsuffisantException(String numeroCompte) {
        super("SOLDE_INSUFFISANT", "Solde insuffisant sur le compte " + numeroCompte, 422);
    }

    public SoldeInsuffisantException(Integer idCompte, BigDecimal montant, BigDecimal solde) {
        super("SOLDE_INSUFFISANT",
                "Solde insuffisant sur le compte " + idCompte + " : montant demandé = " + montant
                        + ", solde actuel = " + solde,
                422);
    }
}
