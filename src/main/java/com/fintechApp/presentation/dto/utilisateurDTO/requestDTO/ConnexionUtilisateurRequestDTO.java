package com.fintechApp.presentation.dto.utilisateurDTO.requestDTO;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnexionUtilisateurRequestDTO {
    private String email;
    private String motPasse;
}
