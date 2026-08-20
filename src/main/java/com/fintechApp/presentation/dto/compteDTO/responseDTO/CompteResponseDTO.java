package com.fintechApp.presentation.dto.compteDTO.responseDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fintechApp.persistance.entity.StatutCompte;
import com.fintechApp.persistance.entity.TypeCompte;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Représentation renvoyée pour la création, la lecture, la liste, la mise à jour, la recharge et le retrait. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompteResponseDTO {
    private Integer idCompte;
    private String numero;
    private Integer utilisateurId;
    private TypeCompte typeCompte;
    private String devise;
    private BigDecimal solde;
    private StatutCompte statut;
    private LocalDateTime dateCreation;
    private LocalDateTime dateMaj;
}
