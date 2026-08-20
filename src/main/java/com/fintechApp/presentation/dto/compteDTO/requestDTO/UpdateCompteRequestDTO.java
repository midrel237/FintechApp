package com.fintechApp.presentation.dto.compteDTO.requestDTO;

import com.fintechApp.persistance.entity.TypeCompte;

import lombok.Data;

@Data
public class UpdateCompteRequestDTO {
    private TypeCompte typeCompte;
}
