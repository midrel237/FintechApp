package com.fintechApp.presentation.dto.compteDTO.requestDTO;

import com.fintechApp.persistance.entity.TypeCompte;

import lombok.Data;

/**
 * L'identifiant de l'utilisateur propriétaire n'est PAS un champ de ce DTO :
 * il est résolu côté serveur depuis le token JWT de l'appelant (voir
 * CompteController), pour éviter qu'un utilisateur puisse créer un compte
 * au nom d'un autre en falsifiant ce champ dans le corps de la requête.
 */
@Data
public class CreateCompteRequestDTO {
    private TypeCompte typeCompte;
    private String devise;
}
