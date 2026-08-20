package com.fintechApp.presentation.dto.utilisateurDTO.responseDTO;

import java.time.LocalDateTime;

import com.fintechApp.persistance.entity.StatutUtilisateur;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReadUtilisateurResponseDTO {
    private Integer id;
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private String adresse;
    private StatutUtilisateur statut;
    private LocalDateTime dateCreation;
}
