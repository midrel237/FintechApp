package com.fintechApp.presentation.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fintechApp.metier.service.JournalComptableService;
import com.fintechApp.persistance.entity.JournalComptable;
import com.fintechApp.presentation.dto.journalComptableDTO.requestDTO.JournalComptableRequestDTO;
import com.fintechApp.presentation.dto.journalComptableDTO.responseDTO.JournalComptablePageResponseDTO;
import com.fintechApp.presentation.dto.journalComptableDTO.responseDTO.JournalComptableResponseDTO;

/**
 * Toutes les routes exigent un token JWT valide (SecurityConfig :
 * anyRequest().authenticated(), aucune exception ajoutée pour
 * /api/v1/journalComptable/**). Récupère un token via
 * POST /api/v1/utilisateurs/connexion, puis Authorization: Bearer <token>.
 *
 * Le journal comptable n'est jamais alimenté directement via cette API : ses
 * lignes "normales" sont générées automatiquement par TransactionService
 * (confirmation d'un virement) et CompteService (recharge/retrait). Seule la
 * contre-passation (4.c) crée une écriture depuis un appel client, puisqu'une
 * ligne existante ne peut jamais être modifiée ni supprimée (RG).
 */
@RestController
@RequestMapping("/api/v1/journalComptable")
public class JournalComptableController {

    private final JournalComptableService journalComptableService;

    public JournalComptableController(JournalComptableService journalComptableService) {
        this.journalComptableService = journalComptableService;
    }

    // GET http://localhost:8080/api/v1/journalComptable?dateDebut=...&dateFin=...&idCompte=...&page=0&taille=20
    // dateDebut/dateFin : "AAAA-MM-JJ" ou "AAAA-MM-JJTHH:mm:ss", tous deux optionnels.
    @GetMapping
    public ResponseEntity<JournalComptablePageResponseDTO> lister(
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin,
            @RequestParam(required = false) Integer idCompte,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int taille) {
        Page<JournalComptable> resultat = journalComptableService.listerJournaux(dateDebut, dateFin, idCompte, page, taille);
        return ResponseEntity.ok(JournalComptablePageResponseDTO.depuis(resultat));
    }

    // GET http://localhost:8080/api/v1/journalComptable/{idJ}
    @GetMapping("/{idJ}")
    public ResponseEntity<JournalComptableResponseDTO> lire(@PathVariable Integer idJ) {
        JournalComptable ligne = journalComptableService.lireJournal(idJ);
        return ResponseEntity.ok(JournalComptableResponseDTO.save(ligne));
    }

    // POST http://localhost:8080/api/v1/journalComptable/{idJ}/nouveauJournal
    @PostMapping("/{idJ}/nouveauJournal")
    public ResponseEntity<JournalComptableResponseDTO> contrePasser(@PathVariable Integer idJ,
                                                                      @RequestBody JournalComptableRequestDTO dto) {
        JournalComptable nouvelleLigne = journalComptableService.creerContrePassation(idJ, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(JournalComptableResponseDTO.save(nouvelleLigne));
    }
}
