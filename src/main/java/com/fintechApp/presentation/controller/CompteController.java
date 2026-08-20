package com.fintechApp.presentation.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fintechApp.metier.service.CompteService;
import com.fintechApp.metier.service.UtilisateurService;
import com.fintechApp.persistance.entity.Compte;
import com.fintechApp.persistance.entity.Utilisateur;
import com.fintechApp.presentation.dto.compteDTO.requestDTO.CreateCompteRequestDTO;
import com.fintechApp.presentation.dto.compteDTO.requestDTO.RechargeCompteRequestDTO;
import com.fintechApp.presentation.dto.compteDTO.requestDTO.RetraitCompteRequestDTO;
import com.fintechApp.presentation.dto.compteDTO.requestDTO.UpdateCompteRequestDTO;
import com.fintechApp.presentation.dto.compteDTO.responseDTO.CompteResponseDTO;
import com.fintechApp.presentation.dto.compteDTO.responseDTO.SoldeCompteResponseDTO;

/**
 * Toutes les routes exigent un token JWT valide (SecurityConfig :
 * anyRequest().authenticated() par défaut, aucune exception ajoutée pour
 * /api/comptes/**). Récupère un token via POST /api/utilisateurs/connexion,
 * puis Authorization: Bearer <token> sur chaque requête Postman ci-dessous.
 */
@RestController
@RequestMapping("/api/v1/comptes")
public class CompteController {

    private final CompteService compteService;
    private final UtilisateurService utilisateurService;

    public CompteController(CompteService compteService, UtilisateurService utilisateurService) {
        this.compteService = compteService;
        this.utilisateurService = utilisateurService;
    }

    // POST http://localhost:8080/api/comptes/creer
    // idUtilisateur résolu depuis le JWT de l'appelant (jamais depuis le
    // corps de la requête) : un utilisateur ne peut créer un compte que
    // pour lui-même.
    @PostMapping("/creer")
    public ResponseEntity<CompteResponseDTO> creer(@RequestBody CreateCompteRequestDTO dto) {
        Integer idUtilisateur = utilisateurConnecte().getId();
        Compte compte = compteService.creerCompte(idUtilisateur, dto.getTypeCompte(), dto.getDevise());
        return ResponseEntity.status(HttpStatus.CREATED).body(versDTO(compte));
    }

    // GET http://localhost:8080/api/comptes/{id}/lire
    @GetMapping("/{id}/lire")
    public ResponseEntity<CompteResponseDTO> lire(@PathVariable Integer id) {
        Compte compte = compteService.lireCompte(id);
        return ResponseEntity.ok(versDTO(compte));
    }

    // GET http://localhost:8080/api/comptes/mes-comptes
    // Liste les comptes de l'utilisateur authentifié (jamais d'un id passé
    // en paramètre : cela permettrait à quiconque de lister les comptes de
    // n'importe qui d'autre en devinant un identifiant).
    @GetMapping("/mes-comptes")
    public ResponseEntity<List<CompteResponseDTO>> mesComptes() {
        Integer idUtilisateur = utilisateurConnecte().getId();
        List<CompteResponseDTO> comptes = compteService.listerComptesParUtilisateur(idUtilisateur)
                .stream().map(this::versDTO).toList();
        return ResponseEntity.ok(comptes);
    }

    // PUT http://localhost:8080/api/comptes/{id}
    @PutMapping("/{id}")
    public ResponseEntity<CompteResponseDTO> modifier(@PathVariable Integer id,
                                                        @RequestBody UpdateCompteRequestDTO dto) {
        Compte compte = compteService.modifierCompte(id, dto.getTypeCompte());
        return ResponseEntity.ok(versDTO(compte));
    }

    // DELETE http://localhost:8080/api/comptes/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Integer id) {
        compteService.supprimerCompte(id);
        return ResponseEntity.noContent().build();
    }

    // POST http://localhost:8080/api/comptes/{id}/recharge
    @PostMapping("/{id}/recharge")
    public ResponseEntity<CompteResponseDTO> recharger(@PathVariable Integer id,
                                                         @RequestBody RechargeCompteRequestDTO dto) {
        Compte compte = compteService.recharger(id, dto.getMontant());
        return ResponseEntity.ok(versDTO(compte));
    }

    // POST http://localhost:8080/api/comptes/{id}/retrait
    @PostMapping("/{id}/retrait")
    public ResponseEntity<CompteResponseDTO> retirer(@PathVariable Integer id,
                                                       @RequestBody RetraitCompteRequestDTO dto) {
        Compte compte = compteService.retirer(id, dto.getMontant());
        return ResponseEntity.ok(versDTO(compte));
    }

    // GET http://localhost:8080/api/comptes/{id}/solde
    @GetMapping("/{id}/solde")
    public ResponseEntity<SoldeCompteResponseDTO> solde(@PathVariable Integer id) {
        BigDecimal solde = compteService.consulterSolde(id);
        Compte compte = compteService.lireCompte(id);
        return ResponseEntity.ok(new SoldeCompteResponseDTO(id, solde, compte.getDevise()));
    }

    // ------------------------------------------------------------------

    private Utilisateur utilisateurConnecte() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return utilisateurService.recupererUtilisateurParEmail(email);
    }

    private CompteResponseDTO versDTO(Compte c) {
        return new CompteResponseDTO(
                c.getIdCompte(), c.getNumero(), c.getIdUtilisateur().getId(),
                c.getType(), c.getDevise(), c.getSolde(), c.getStatut(),
                c.getDateCreation(), c.getDateMaj());
    }
}
