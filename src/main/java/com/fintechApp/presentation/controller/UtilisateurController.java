package com.fintechApp.presentation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fintechApp.infrastructure.security.jwt.JwtUtils;
import com.fintechApp.metier.service.UtilisateurService;
import com.fintechApp.persistance.entity.Utilisateur;
import com.fintechApp.presentation.dto.utilisateurDTO.requestDTO.ConnexionUtilisateurRequestDTO;
import com.fintechApp.presentation.dto.utilisateurDTO.requestDTO.CreateUtilisateurRequestDTO;
import com.fintechApp.presentation.dto.utilisateurDTO.requestDTO.UpdateUtilisateurRequestDto;
import com.fintechApp.presentation.dto.utilisateurDTO.requestDTO.ValidUtilisateurResquestDTO;
import com.fintechApp.presentation.dto.utilisateurDTO.responseDTO.ConnexionUtilisateurResponseDTO;
import com.fintechApp.presentation.dto.utilisateurDTO.responseDTO.CreateUtilisateurResponseDTO;
import com.fintechApp.presentation.dto.utilisateurDTO.responseDTO.ReadUtilisateurResponseDTO;
import com.fintechApp.presentation.dto.utilisateurDTO.responseDTO.UpdateUtilisateurResponseDTO;

@RestController
@RequestMapping("/api/v1/utilisateurs")
public class UtilisateurController {

    private final UtilisateurService utilisateurService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    public UtilisateurController(UtilisateurService utilisateurService,
                                  AuthenticationManager authenticationManager,
                                  JwtUtils jwtUtils) {
        this.utilisateurService = utilisateurService;
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
    }

    // POST http://localhost:8080/api/utilisateurs/creer
    // Le code de validation est généré et envoyé par email directement ici
    // (dans creerUtilisateur) : il n'y a plus de route /envoyerCode séparée.
    @PostMapping("/creer")
    public ResponseEntity<CreateUtilisateurResponseDTO> creer(@RequestBody CreateUtilisateurRequestDTO dto) {
        Utilisateur u = utilisateurService.creerUtilisateur(dto);
        CreateUtilisateurResponseDTO reponse = new CreateUtilisateurResponseDTO(
                u.getId(), u.getNom(), u.getPrenom(), u.getEmail(),
                u.getTelephone(), u.getAdresse(), u.getStatut(), u.getDateCreation());
        return ResponseEntity.status(HttpStatus.CREATED).body(reponse);
    }

    // POST http://localhost:8080/api/utilisateurs/valider
    // Corrige l'ancien bug : un seul corps de requête (DTO), et non deux
    // @RequestBody sur la même méthode, ce que Spring MVC ne supporte pas.
    @PostMapping("/valider")
    public ResponseEntity<String> validerUtilisateur(@RequestBody ValidUtilisateurResquestDTO dto) {
        boolean estValide = utilisateurService.validerUtilisateur(dto.getEmail(), dto.getCodeSaisi());
        if (estValide) {
            return ResponseEntity.ok("Utilisateur validé avec succès.");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Code de validation incorrect.");
        }
    }

    // POST http://localhost:8080/api/utilisateurs/connexion
    // L'authentification (email + mot de passe haché) est entièrement
    // déléguée à Spring Security via AuthenticationManager, qui s'appuie sur
    // UtilisateurDetailsService + le PasswordEncoder (bean défini dans
    // SecurityConfig). Aucune comparaison de mot de passe en clair ici.
    @PostMapping("/connexion")
    public ResponseEntity<ConnexionUtilisateurResponseDTO> connecterUtilisateur(
            @RequestBody ConnexionUtilisateurRequestDTO dto) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getMotPasse()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String jwt = jwtUtils.generateToken(authentication);
        Utilisateur utilisateur = utilisateurService.recupererUtilisateurParEmail(dto.getEmail());

        ConnexionUtilisateurResponseDTO reponse = new ConnexionUtilisateurResponseDTO(
                jwt, "Bearer", utilisateur.getId(), utilisateur.getNom(),
                utilisateur.getPrenom(), utilisateur.getEmail());

        return ResponseEntity.ok(reponse);
    }

    // POST http://localhost:8080/api/utilisateurs/deconnexion
    // Nécessite un header "Authorization: Bearer <token>" valide (route
    // protégée par SecurityConfig). L'email n'est plus attendu dans le corps
    // de la requête : il est lu depuis le token JWT déjà validé par
    // JwtAuthenticationFilter, ce qui évite d'avoir à envoyer depuis Postman
    // un corps JSON à la forme peu standard (chaîne brute).
    @PostMapping("/deconnexion")
    public ResponseEntity<String> deconnecterUtilisateur() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        utilisateurService.deconnecterUtilisateur(email);
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Déconnexion réussie.");
    }

    // GET http://localhost:8080/api/utilisateurs/{id}/lire
    // Ordre du chemin aligné sur le contrat d'API (Partie 2, 1.b) :
    // /api/utilisateurs/{idU}/lire, et non /lire/{id} comme précédemment.
    @GetMapping("/{id}/lire")
    public ResponseEntity<ReadUtilisateurResponseDTO> obtenirUtilisateur(@PathVariable Integer id) {
        Utilisateur u = utilisateurService.recupererUtilisateurParId(id);
        ReadUtilisateurResponseDTO reponse = new ReadUtilisateurResponseDTO(
                u.getId(), u.getNom(), u.getPrenom(), u.getEmail(),
                u.getTelephone(), u.getAdresse(), u.getStatut(), u.getDateCreation());
        return ResponseEntity.ok(reponse);
    }

    // PATCH http://localhost:8080/api/utilisateurs/{id}
    @PatchMapping("/{id}")
    public ResponseEntity<UpdateUtilisateurResponseDTO> mettreAJourUtilisateur(
            @PathVariable Integer id, @RequestBody UpdateUtilisateurRequestDto dto) {
        Utilisateur u = utilisateurService.mettreAJourUtilisateur(id, dto);
        UpdateUtilisateurResponseDTO reponse = new UpdateUtilisateurResponseDTO(
                u.getId(), u.getNom(), u.getPrenom(), u.getEmail(),
                u.getTelephone(), u.getAdresse(), u.getStatut(), u.getDateMaj());
        return ResponseEntity.ok(reponse);
    }

    // DELETE http://localhost:8080/api/utilisateurs/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<String> supprimerUtilisateur(@PathVariable Integer id) {
        utilisateurService.supprimerUtilisateur(id);
        return ResponseEntity.ok("Utilisateur supprimé avec succès.");
    }
}
