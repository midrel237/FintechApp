package com.fintechApp.presentation.dto.utilisateurDTO.responseDTO;

import java.time.LocalDateTime;

import com.fintechApp.persistance.entity.StatutUtilisateur;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Le mot de passe (même haché) n'a rien à faire dans une réponse HTTP :
// le champ motPasse a été retiré de ce DTO (il était présent avant, ce qui
// exposait le hash BCrypt dans le JSON renvoyé au client).
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUtilisateurResponseDTO {
    private Integer id;
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private String adresse;
    private StatutUtilisateur statut;
    private LocalDateTime dateMaj;
}
