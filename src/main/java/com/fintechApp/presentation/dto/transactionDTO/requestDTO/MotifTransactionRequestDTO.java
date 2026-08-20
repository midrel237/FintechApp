package com.fintechApp.presentation.dto.transactionDTO.requestDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Corps de requête commun à PUT /api/transactions/{id}/annuler et
 * PUT /api/transactions/{id}/suspend : le motif est optionnel côté client
 * (le schéma SQL ne porte pas de colonne motif sur la table transaction ;
 * il n'est aujourd'hui utilisé qu'à des fins de traçabilité applicative).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MotifTransactionRequestDTO {
    private String motif;
}
