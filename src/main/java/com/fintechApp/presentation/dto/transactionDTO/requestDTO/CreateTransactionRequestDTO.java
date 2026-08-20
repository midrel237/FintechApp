package com.fintechApp.presentation.dto.transactionDTO.requestDTO;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateTransactionRequestDTO {
    private Integer CompteDestination;
    private BigDecimal montant;
    private String description;

}
