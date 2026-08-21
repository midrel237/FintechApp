package com.fintechApp.metier.service;


import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fintechApp.metier.exception.UtilisateurNonTrouveException;
import com.fintechApp.metier.exception.ValidationException;
import com.fintechApp.persistance.entity.StatutUtilisateur;
import com.fintechApp.persistance.entity.Utilisateur;
import com.fintechApp.persistance.repository.UtilisateurRepository;
import com.fintechApp.presentation.dto.utilisateurDTO.requestDTO.CreateUtilisateurRequestDTO;
import com.fintechApp.presentation.dto.utilisateurDTO.requestDTO.UpdateUtilisateurRequestDto;

import jakarta.transaction.Transactional;


@Service
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public UtilisateurService(UtilisateurRepository utilisateurRepository,
                               EmailService emailService,
                               PasswordEncoder passwordEncoder) {
        this.utilisateurRepository = utilisateurRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Utilisateur creerUtilisateur(CreateUtilisateurRequestDTO dto) {
        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new ValidationException("L'email est obligatoire.");
        }
        if (dto.getMotPasse() == null || dto.getMotPasse().isBlank()) {
            throw new ValidationException("Le mot de passe est obligatoire.");
        }
        // RG A : "un utilisateur est identifié de manière unique par son adresse mail"
        if (utilisateurRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new ValidationException("Un utilisateur existe déjà avec cet email.");
        }

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setNom(dto.getNom());
        utilisateur.setPrenom(dto.getPrenom());
        utilisateur.setEmail(dto.getEmail());
        utilisateur.setTelephone(dto.getTelephone());
        utilisateur.setAdresse(dto.getAdresse());

        // RG A : "le mot de passe ne doit jamais être stocké en clair ; il doit
        // utiliser un algorithme de hachage." -> BCrypt via le PasswordEncoder
        // injecté (bean défini dans SecurityConfig), jamais le mot de passe brut.
        utilisateur.setMotPasse(passwordEncoder.encode(dto.getMotPasse()));

        utilisateur.setDateCreation(LocalDateTime.now());
        utilisateur.setDateMaj(LocalDateTime.now());
        utilisateur.setStatut(StatutUtilisateur.verrouille); // Statut initial "verrouillé"

        // Le code de validation est désormais généré et envoyé directement à
        // l'inscription (il n'existe plus de route /envoyerCode séparée) :
        // l'utilisateur reçoit son code par email dès la création du compte.
        String code = String.valueOf((int) (Math.random() * 900000) + 100000);
        utilisateur.setCodeValidation(code);
        utilisateur.setExpirationCode(LocalDateTime.now().plusHours(24));

        Utilisateur utilisateurCree = utilisateurRepository.save(utilisateur);

        emailService.envoyerEmail(utilisateurCree.getEmail(), code);

        return utilisateurCree;
    }

    /**
     * Valide un utilisateur avec le code reçu par email.
     *
     * Corrige un bug critique de la version précédente : la condition était
     * inversée et activait le compte quand le code NE correspondait PAS.
     * Ajoute également deux vérifications qui manquaient totalement :
     * - le code a une date d'expiration (fixée à la création) qui n'était
     *   jamais vérifiée ;
     * - un compte déjà ACTIF ne doit pas pouvoir être "revalidé".
     */
    @Transactional
public boolean validerUtilisateur(String email, String codeSaisi) {
    Utilisateur utilisateur = utilisateurRepository.findByEmail(email)
            .orElseThrow(() -> new UtilisateurNonTrouveException("Utilisateur non trouvé avec l'email: " + email));

    if (utilisateur.getStatut() == StatutUtilisateur.actif) {
        throw new ValidationException("Ce compte est déjà validé.");
    }

    if (utilisateur.getExpirationCode() != null
            && LocalDateTime.now().isAfter(utilisateur.getExpirationCode())) {
        throw new ValidationException("Le code de validation a expiré. Merci de refaire une demande.");
    }

    if (utilisateur.getCodeValidation() != null && utilisateur.getCodeValidation().equals(codeSaisi)) {
        
        utilisateur.setStatut(StatutUtilisateur.actif);
        utilisateur.setCodeValidation(null);
        utilisateur.setExpirationCode(null);
        utilisateur.setDateMaj(LocalDateTime.now());
        utilisateurRepository.save(utilisateur);
        return true;
    }

    return false; // code incorrect
}

    @Transactional
    public Utilisateur recupererUtilisateurParId(Integer id) {
        return utilisateurRepository.findById(id)
                .orElseThrow(() -> new UtilisateurNonTrouveException("Utilisateur non trouvé avec l'ID: " + id));
    }

    /**
     * Utilisé après une authentification réussie (AuthenticationManager) pour
     * reconstruire la réponse de connexion (id, nom, prénom...). La
     * vérification du mot de passe elle-même est déléguée à Spring Security
     * (UtilisateurDetailsService + PasswordEncoder), jamais faite ici en clair.
     */
    @Transactional
    public Utilisateur recupererUtilisateurParEmail(String email) {
        return utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new UtilisateurNonTrouveException("Utilisateur non trouvé avec l'email: " + email));
    }

    /**
     * Met à jour les informations d'un utilisateur. Tous les champs du DTO
     * sont optionnels : un champ non fourni (null) laisse la valeur actuelle
     * inchangée, au lieu d'écraser nom/prénom/email/mot de passe avec des
     * valeurs vides comme le faisait l'ancienne implémentation.
     */
    @Transactional
    public Utilisateur mettreAJourUtilisateur(Integer id, UpdateUtilisateurRequestDto dto) {
        Utilisateur utilisateur = recupererUtilisateurParId(id);

        if (dto.getNom() != null) {
            utilisateur.setNom(dto.getNom());
        }
        if (dto.getPrenom() != null) {
            utilisateur.setPrenom(dto.getPrenom());
        }
        if (dto.getEmail() != null) {
            utilisateur.setEmail(dto.getEmail());
        }
        if (dto.getTelephone() != null) {
            utilisateur.setTelephone(dto.getTelephone());
        }
        if (dto.getAdresse() != null) {
            utilisateur.setAdresse(dto.getAdresse());
        }
        if (dto.getMotPasse() != null && !dto.getMotPasse().isBlank()) {
            utilisateur.setMotPasse(passwordEncoder.encode(dto.getMotPasse()));
        }
        utilisateur.setDateMaj(LocalDateTime.now());

        return utilisateurRepository.save(utilisateur);
    }

    // Déconnexion : dans un schéma JWT stateless sans liste de révocation,
    // il n'y a rien à faire côté serveur (le client se contente d'oublier
    // son token). On garde la méthode pour l'endpoint /deconnexion, qui
    // vérifie simplement que l'utilisateur existe.
    @Transactional
    public void deconnecterUtilisateur(String email) {
        utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new UtilisateurNonTrouveException("Utilisateur non trouvé avec l'email: " + email));
    }



    @Transactional
    public Utilisateur connecterUtilisateur(String email, String motPasse) {
        Utilisateur utilisateur = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new UtilisateurNonTrouveException(email));
        
        // Vérification de sécurité : le compte est-il toujours verrouillé ?
        if (utilisateur.getStatut() == StatutUtilisateur.verrouille) {
            throw new RuntimeException("Votre compte n'est pas encore activé. Veuillez vérifier votre e-mail.");
        }

        // Vérification du mot de passe
        if (!utilisateur.getMotPasse().equals(motPasse)) {
            throw new RuntimeException("Mot de passe incorrect : Veuillez entrer un mot de passe valide");
        }
    
        return utilisateur;

    }

    @Transactional
    public void supprimerUtilisateur(Integer id) {
        Utilisateur utilisateur = recupererUtilisateurParId(id);
        utilisateurRepository.delete(utilisateur);
    }
}
