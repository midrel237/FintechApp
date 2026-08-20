package com.fintechApp.presentation.dto.compteDTO.responseDTO;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SoldeCompteResponseDTO {
    private Integer idCompte;
    private BigDecimal solde;
    private String devise;
}
