package com.fintechApp.presentation.dto.transactionDTO.responseDTO;


import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fintechApp.persistance.entity.Transaction;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * DTO de sortie commun aux endpoints /api/v1/transactions/*.
 */
@Data
@AllArgsConstructor
public class TransactionResponse {

    private Integer id;
    private String reference;
    private String description;
    private String statut;
    private BigDecimal montant;
    private Integer CompteSource;
    private Integer CompteDestination;
    private LocalDateTime dateCreation;
    private LocalDateTime dateValidation;
    private LocalDateTime dateAnnulation;
    private LocalDateTime dateSuspension;

    /**
     * Identifiant de la ligne de journal comptable générée à la
     * confirmation (partie double, une seule ligne pour le débit et le
     * crédit — voir JournalComptableService#enregistrerVirement). Reste
     * null pour toute transaction qui n'a pas encore été confirmée.
     */
    private Integer ligneJournalId;

    /**
     * Construit le DTO à partir de l'entité persistée, avant confirmation
     * (ligneJournalId reste donc null ici).
     */
    public static TransactionResponse save(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getReference(),
                transaction.getDescription(),
                transaction.getStatut() != null ? transaction.getStatut().name() : null,
                transaction.getMontant(),
                transaction.getCompteSource() != null ? transaction.getCompteSource().getIdCompte() : null,
                transaction.getCompteDestination() != null ? transaction.getCompteDestination().getIdCompte() : null,
                transaction.getDateCreation(),
                transaction.getDateValidation(),
                transaction.getDateAnnulation(),
                transaction.getDateSuspension(),
                null);
    }
}
