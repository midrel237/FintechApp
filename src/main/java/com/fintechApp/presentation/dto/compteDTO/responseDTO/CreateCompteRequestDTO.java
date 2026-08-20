package com.fintechApp.presentation.dto.compteDTO.responseDTO;

import com.fintechApp.persistance.entity.TypeCompte;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateCompteRequestDTO {
    private Integer idCompte;
    private Integer utilisateurId;
    private TypeCompte typeCompte;
    private String devise;
    private BigDecimal solde;
    private LocalDateTime dateCreation;
}
