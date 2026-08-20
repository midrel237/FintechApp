package com.fintechApp.presentation.dto.utilisateurDTO.requestDTO;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUtilisateurRequestDTO {
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private String adresse;
    private String motPasse;
}
