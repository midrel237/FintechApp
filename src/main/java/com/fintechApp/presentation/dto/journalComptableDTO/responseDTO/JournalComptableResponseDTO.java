package com.fintechApp.presentation.dto.journalComptableDTO.responseDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fintechApp.persistance.entity.JournalComptable;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * DTO de sortie commun aux trois endpoints /api/journalComptable/* (4.a
 * élément de liste, 4.b lecture unitaire, 4.c contre-passation). motif et
 * idLigneOrigine restent à null pour une ligne "normale" (générée par
 * TransactionService#confirmerTransaction ou par CompteService via une
 * recharge/un retrait) et ne sont renseignés que pour une ligne de
 * correction créée via 4.c.
 */
@Data
@AllArgsConstructor
public class JournalComptableResponseDTO {

    private Integer idJ;
    private LocalDateTime dateEnregistrement;
    private String numCompteDebit;
    private String numCompteCredit;
    private BigDecimal montantDebit;
    private BigDecimal montantCredit;
    private String refT;
    private String libelleJ;
    private String motif;
    private Integer idLigneOrigine;

    public static JournalComptableResponseDTO save(JournalComptable ligne) {
        return new JournalComptableResponseDTO(
                ligne.getId(),
                ligne.getDateEnregistrement(),
                ligne.getCompteDebit() != null ? ligne.getCompteDebit().getNumero() : null,
                ligne.getCompteCredit() != null ? ligne.getCompteCredit().getNumero() : null,
                ligne.getMontantDebit(),
                ligne.getMontantCredit(),
                ligne.getTransaction() != null ? ligne.getTransaction().getReference() : null,
                ligne.getLibelle(),
                ligne.getMotif(),
                ligne.getLigneOrigine() != null ? ligne.getLigneOrigine().getId() : null);
    }
}
