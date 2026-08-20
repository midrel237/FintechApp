package com.fintechApp.presentation.dto.utilisateurDTO.requestDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ValidUtilisateurResquestDTO {
    private String email;
    private String codeSaisi;

}
