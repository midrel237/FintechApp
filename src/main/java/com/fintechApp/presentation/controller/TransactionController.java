package com.fintechApp.presentation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fintechApp.metier.service.TransactionService;
import com.fintechApp.metier.service.UtilisateurService;
import com.fintechApp.persistance.entity.Utilisateur;
import com.fintechApp.presentation.dto.transactionDTO.requestDTO.CreateTransactionRequestDTO;
import com.fintechApp.presentation.dto.transactionDTO.requestDTO.MotifTransactionRequestDTO;
import com.fintechApp.presentation.dto.transactionDTO.responseDTO.TransactionResponse;

/**
 * Toutes les routes exigent un token JWT valide (SecurityConfig :
 * anyRequest().authenticated() par défaut, aucune exception ajoutée pour
 * /api/v1/transactions/**). Récupère un token via POST /api/v1/utilisateurs/connexion,
 * puis Authorization: Bearer <token> sur chaque requête Postman ci-dessous.
 */
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final UtilisateurService utilisateurService;

    public TransactionController(TransactionService transactionService, UtilisateurService utilisateurService) {
        this.transactionService = transactionService;
        this.utilisateurService = utilisateurService;
    }

    // POST http://localhost:8080/api/v1/transactions/{idCompteSource}/creer
    // idUtilisateur résolu depuis le JWT de l'appelant (jamais depuis le
    // corps de la requête), comme dans CompteController.
    @PostMapping("/{idCompteSource}/creer")
    public ResponseEntity<TransactionResponse> creer(@PathVariable Integer idCompteSource,
                                                       @RequestBody CreateTransactionRequestDTO dto) {
        Integer idUtilisateur = utilisateurConnecte().getId();
        TransactionResponse reponse = transactionService.creerTransaction(idCompteSource, idUtilisateur, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(reponse);
    }

    // GET http://localhost:8080/api/v1/transactions/{id}/lire
    @GetMapping("/{id}/lire")
    public ResponseEntity<TransactionResponse> lire(@PathVariable Integer id) {
        return ResponseEntity.ok(transactionService.lireTransaction(id));
    }

    // PATCH http://localhost:8080/api/v1/transactions/{id}/confirmer
    // Confirme (valide) une transaction en attente : débite/crédite les
    // comptes et génère l'écriture comptable.
    @PatchMapping("/{id}/confirmer")
    public ResponseEntity<TransactionResponse> confirmer(@PathVariable Integer id) {
        return ResponseEntity.ok(transactionService.confirmerTransaction(id));
    }

    // PUT http://localhost:8080/api/v1/transactions/{id}/annuler
    @PutMapping("/{id}/annuler")
    public ResponseEntity<TransactionResponse> annuler(@PathVariable Integer id,
                                                         @RequestBody(required = false) MotifTransactionRequestDTO dto) {
        String motif = dto != null ? dto.getMotif() : null;
        return ResponseEntity.ok(transactionService.annulerTransaction(id, motif));
    }

    // PUT http://localhost:8080/api/v1/transactions/{id}/suspend
    @PutMapping("/{id}/suspend")
    public ResponseEntity<TransactionResponse> suspendre(@PathVariable Integer id,
                                                           @RequestBody(required = false) MotifTransactionRequestDTO dto) {
        String motif = dto != null ? dto.getMotif() : null;
        return ResponseEntity.ok(transactionService.suspendreTransaction(id, motif));
    }

    // ------------------------------------------------------------------

    private Utilisateur utilisateurConnecte() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return utilisateurService.recupererUtilisateurParEmail(email);
    }
}
