package com.fintechApp.presentation.dto.utilisateurDTO.requestDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Champs modifiables via PATCH /api/utilisateurs/{id}. Tous les champs sont
 * optionnels (null = non modifié) : voir UtilisateurService.mettreAJourUtilisateur.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUtilisateurRequestDto {
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private String adresse;
    private String motPasse;
}
