package com.fintechApp.presentation.dto.utilisateurDTO.responseDTO;


import lombok.Data;
import lombok.NoArgsConstructor;

// Conservé pour compatibilité mais non utilisé par UtilisateurController,
// qui renvoie désormais ConnexionUtilisateurResponseDTO (plus complet :
// inclut aussi l'identité de l'utilisateur connecté, pas seulement le token).
@Data
@NoArgsConstructor
public class JwtResponseDTO {
    private String token;
    private String type = "Bearer";

    public JwtResponseDTO(String jwt) {
        this.token = jwt;
    }
}
