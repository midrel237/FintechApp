package com.fintechApp.presentation.dto.utilisateurDTO.responseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnexionUtilisateurResponseDTO {
    private String token;
    private String type = "Bearer";
    private Integer id;
    private String nom;
    private String prenom;
    private String email;

}
