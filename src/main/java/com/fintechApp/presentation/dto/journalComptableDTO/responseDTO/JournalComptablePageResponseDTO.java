package com.fintechApp.presentation.dto.journalComptableDTO.responseDTO;

import java.util.List;

import org.springframework.data.domain.Page;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * DTO de sortie de 4.a) GET /api/journalComptable : page, taille, total
 * (métadonnées de pagination) + elements (tableau des lignes de journal).
 */
@Data
@AllArgsConstructor
public class JournalComptablePageResponseDTO {
    private int page;
    private int taille;
    private long total;
    private List<JournalComptableResponseDTO> elements;

    public static JournalComptablePageResponseDTO depuis(Page<com.fintechApp.persistance.entity.JournalComptable> resultat) {
        List<JournalComptableResponseDTO> elements = resultat.getContent().stream()
                .map(JournalComptableResponseDTO::save)
                .toList();
        return new JournalComptablePageResponseDTO(
                resultat.getNumber(), resultat.getSize(), resultat.getTotalElements(), elements);
    }
}
