package com.fintechApp.presentation.dto.journalComptableDTO.requestDTO;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Corps de POST /api/journalComptable/{idJ}/nouveauJournal (contre-passation,
 * Partie 2, 4.c). L'immuabilité du journal comptable (RG : "aucune ligne ne
 * peut être supprimée ou modifiée") impose de corriger une ligne existante en
 * créant une nouvelle ligne distincte qui la référence (idJ_origine), au lieu
 * de la modifier — d'où un DTO dédié plutôt qu'une réutilisation du DTO de
 * transaction.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JournalComptableRequestDTO {
    private String numCompteDebit;
    private String numCompteCredit;
    private BigDecimal montantDebit;
    private BigDecimal montantCredit;
    private String libelleJ;
    private String refT;
    private String motif;
}
