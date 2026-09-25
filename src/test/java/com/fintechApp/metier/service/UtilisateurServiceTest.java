package com.fintechApp.metier.service;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mockito;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.security.crypto.password.PasswordEncoder;

import com.fintechApp.metier.exception.UtilisateurNonTrouveException;
import com.fintechApp.metier.exception.ValidationException;
import com.fintechApp.persistance.entity.StatutUtilisateur;
import com.fintechApp.persistance.entity.Utilisateur;
import com.fintechApp.persistance.repository.UtilisateurRepository;
import com.fintechApp.presentation.dto.utilisateurDTO.requestDTO.CreateUtilisateurRequestDTO;
import com.fintechApp.presentation.dto.utilisateurDTO.requestDTO.UpdateUtilisateurRequestDto;

/**
 * Couvre le niveau "Tests unitaires" du plan de test (FintechApp_Plan_de_test.docx,
 * section 4.1) pour le module Utilisateur : cas UT-V01 à UT-V26, plus les cas
 * fonctionnels de base (création, validation, mise à jour, connexion,
 * déconnexion, suppression) déjà couverts au niveau service.
 *
 * Deux cas du plan ne sont PAS couverts ici, volontairement :
 * - UT-V12 (Content-Type de la requête) : décision prise par Spring MVC avant
 *   que UtilisateurService ne soit même invoqué — relève d'un test contrôleur
 *   (MockMvc), pas d'un test unitaire de service.
 * - UT-V15 (tentative d'injection SQL) : aucune branche de code dédiée dans le
 *   service ; la protection vient de l'utilisation de JPA/Hibernate avec des
 *   requêtes paramétrées (garantie architecturale, pas une règle testable
 *   isolément ici).
 */
class UtilisateurServiceTest {

    // ------------------------------------------------------------------
    // creerUtilisateur — UT-01, UT-02, UT-V01 à UT-V16
    // ------------------------------------------------------------------

    @Test
    void testCreerUtilisateur() {
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        EmailService emailService = Mockito.mock(EmailService.class);
        PasswordEncoder encoder = Mockito.mock(PasswordEncoder.class);
        when(utilisateurs.findByEmailIgnoreCase("jean.dupont@mail.com")).thenReturn(Optional.empty());
        when(encoder.encode("Abcdefg1")).thenReturn("$2a$10$hash");
        when(utilisateurs.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        Utilisateur resultat = service(utilisateurs, emailService, encoder)
                .creerUtilisateur(dto("Dupont", "Jean", "jean.dupont@mail.com", "690000000", "Douala", "Abcdefg1"));

        assertEquals("Dupont", resultat.getNom());
        assertEquals("jean.dupont@mail.com", resultat.getEmail());
        assertEquals("$2a$10$hash", resultat.getMotPasse()); // UT-04 : jamais le mot de passe en clair
        assertEquals(StatutUtilisateur.verrouille, resultat.getStatut());
        assertNotNull(resultat.getCodeValidation());
        assertEquals(6, resultat.getCodeValidation().length());
        assertNotNull(resultat.getExpirationCode());
        assertTrue(resultat.getExpirationCode().isAfter(LocalDateTime.now().plusHours(23)));
        verify(emailService).envoyerEmail("jean.dupont@mail.com", resultat.getCodeValidation());
    }

    @Test
    void testCreerUtilisateurRejetteChampsObligatoiresManquants() {
        UtilisateurService service = service(Mockito.mock(UtilisateurRepository.class),
                Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class));

        // UT-V01, UT-V02, UT-V03, UT-V06, UT-V08, UT-V09
        assertThrows(ValidationException.class,
                () -> service.creerUtilisateur(dto(null, "Jean", "jean@mail.com", "690000000", "Douala", "Abcdefg1")));
        assertThrows(ValidationException.class,
                () -> service.creerUtilisateur(dto("Dupont", " ", "jean@mail.com", "690000000", "Douala", "Abcdefg1")));
        assertThrows(ValidationException.class,
                () -> service.creerUtilisateur(dto("Dupont", "Jean", null, "690000000", "Douala", "Abcdefg1")));
        assertThrows(ValidationException.class,
                () -> service.creerUtilisateur(dto("Dupont", "Jean", "jean@mail.com", null, "Douala", "Abcdefg1")));
        assertThrows(ValidationException.class,
                () -> service.creerUtilisateur(dto("Dupont", "Jean", "jean@mail.com", "690000000", "", "Abcdefg1")));
        assertThrows(ValidationException.class,
                () -> service.creerUtilisateur(dto("Dupont", "Jean", "jean@mail.com", "690000000", "Douala", null)));
    }

    @Test
    void testCreerUtilisateurRejetteEmailFormatInvalide() {
        // UT-V04
        UtilisateurService service = service(Mockito.mock(UtilisateurRepository.class),
                Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class));

        for (String email : new String[] { "jean.dupont", "jean@", "jean@@mail.com", "jean mail.com" }) {
            assertThrows(ValidationException.class,
                    () -> service.creerUtilisateur(dto("Dupont", "Jean", email, "690000000", "Douala", "Abcdefg1")),
                    "email attendu invalide : " + email);
        }
    }

    @Test
    void testCreerUtilisateurRejetteMotDePasseFaible() {
        // UT-V10 (trop court), UT-V11 (sans majuscule / sans chiffre)
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        when(utilisateurs.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        UtilisateurService service = service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class));

        for (String motPasse : new String[] { "Ab1", "abcdefg1", "Abcdefgh" }) {
            assertThrows(ValidationException.class,
                    () -> service.creerUtilisateur(dto("Dupont", "Jean", "jean@mail.com", "690000000", "Douala", motPasse)),
                    "mot de passe attendu rejeté : " + motPasse);
        }
    }

    @Test
    void testCreerUtilisateurRejetteEmailDejaUtilise() {
        // UT-02
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        when(utilisateurs.findByEmailIgnoreCase("jean@mail.com"))
                .thenReturn(Optional.of(utilisateur(1, "jean@mail.com", StatutUtilisateur.actif)));
        UtilisateurService service = service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class));

        assertThrows(ValidationException.class,
                () -> service.creerUtilisateur(dto("Dupont", "Jean", "jean@mail.com", "690000000", "Douala", "Abcdefg1")));
    }

    @Test
    void testCreerUtilisateurVerifieUniciteEmailSansSensibiliteALaCasse() {
        // UT-V05 : au niveau service, on ne peut vérifier que la bonne méthode
        // de repository est appelée (findByEmailIgnoreCase et non findByEmail) —
        // l'insensibilité à la casse elle-même est garantie par Spring Data JPA,
        // hors périmètre d'un test unitaire du service.
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        when(utilisateurs.findByEmailIgnoreCase("Jean.Dupont@Mail.COM")).thenReturn(Optional.empty());
        when(utilisateurs.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));
        service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class))
                .creerUtilisateur(dto("Dupont", "Jean", "Jean.Dupont@Mail.COM", "690000000", "Douala", "Abcdefg1"));
        verify(utilisateurs).findByEmailIgnoreCase("Jean.Dupont@Mail.COM");
    }

    @Test
    void testCreerUtilisateurTrimmeLesEspacesSuperflus() {
        // UT-V13
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        when(utilisateurs.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(utilisateurs.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        Utilisateur resultat = service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class))
                .creerUtilisateur(dto("  Dupont  ", "  Jean  ", " jean@mail.com ", " 690000000 ", " Douala ", "Abcdefg1"));

        assertEquals("Dupont", resultat.getNom());
        assertEquals("Jean", resultat.getPrenom());
        assertEquals("jean@mail.com", resultat.getEmail());
    }

    @Test
    void testCreerUtilisateurAccepteApostropheTiretEtAccents() {
        // UT-V14
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        when(utilisateurs.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(utilisateurs.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        Utilisateur resultat = service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class))
                .creerUtilisateur(dto("N'Guessan", "Jean-Pierre", "eric@mail.com", "690000000", "Douala", "Abcdefg1"));

        assertEquals("N'Guessan", resultat.getNom());
        assertEquals("Jean-Pierre", resultat.getPrenom());
    }

    // ------------------------------------------------------------------
    // validerUtilisateur — UT-05 à UT-08
    // ------------------------------------------------------------------

    @Test
    void testValiderUtilisateur() {
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        Utilisateur utilisateur = utilisateur(1, "jean@mail.com", StatutUtilisateur.verrouille);
        utilisateur.setCodeValidation("123456");
        utilisateur.setExpirationCode(LocalDateTime.now().plusHours(1));
        when(utilisateurs.findByEmailIgnoreCase("jean@mail.com")).thenReturn(Optional.of(utilisateur));

        boolean resultat = service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class))
                .validerUtilisateur("jean@mail.com", "123456");

        assertTrue(resultat);
        assertEquals(StatutUtilisateur.actif, utilisateur.getStatut());
        assertEquals(null, utilisateur.getCodeValidation());
        verify(utilisateurs).save(utilisateur);
    }

    @Test
    void testValiderUtilisateurCodeIncorrectRenvoieFaux() {
        // UT-06
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        Utilisateur utilisateur = utilisateur(1, "jean@mail.com", StatutUtilisateur.verrouille);
        utilisateur.setCodeValidation("123456");
        utilisateur.setExpirationCode(LocalDateTime.now().plusHours(1));
        when(utilisateurs.findByEmailIgnoreCase("jean@mail.com")).thenReturn(Optional.of(utilisateur));

        boolean resultat = service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class))
                .validerUtilisateur("jean@mail.com", "000000");

        assertFalse(resultat);
        assertEquals(StatutUtilisateur.verrouille, utilisateur.getStatut());
        verify(utilisateurs, never()).save(any(Utilisateur.class));
    }

    @Test
    void testValiderUtilisateurRejetteCodeExpireOuDejaValide() {
        // UT-07, UT-08
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        UtilisateurService service = service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class));

        Utilisateur expire = utilisateur(1, "jean@mail.com", StatutUtilisateur.verrouille);
        expire.setCodeValidation("123456");
        expire.setExpirationCode(LocalDateTime.now().minusMinutes(1));
        when(utilisateurs.findByEmailIgnoreCase("jean@mail.com")).thenReturn(Optional.of(expire));
        assertThrows(ValidationException.class, () -> service.validerUtilisateur("jean@mail.com", "123456"));

        Utilisateur dejaActif = utilisateur(2, "paul@mail.com", StatutUtilisateur.actif);
        when(utilisateurs.findByEmailIgnoreCase("paul@mail.com")).thenReturn(Optional.of(dejaActif));
        assertThrows(ValidationException.class, () -> service.validerUtilisateur("paul@mail.com", "123456"));
    }

    @Test
    void testValiderUtilisateurRejetteEmailInconnu() {
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        when(utilisateurs.findByEmailIgnoreCase("inconnu@mail.com")).thenReturn(Optional.empty());
        UtilisateurService service = service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class));
        assertThrows(UtilisateurNonTrouveException.class, () -> service.validerUtilisateur("inconnu@mail.com", "123456"));
    }

    // ------------------------------------------------------------------
    // mettreAJourUtilisateur — UT-17, UT-18, UT-V23 à UT-V26
    // ------------------------------------------------------------------

    @Test
    void testMettreAJourUtilisateur() {
        // UT-17 : mise à jour partielle, les champs non fournis restent inchangés
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        Utilisateur utilisateur = utilisateur(1, "jean@mail.com", StatutUtilisateur.actif);
        utilisateur.setNom("Dupont");
        when(utilisateurs.findById(1)).thenReturn(Optional.of(utilisateur));
        when(utilisateurs.save(utilisateur)).thenReturn(utilisateur);

        Utilisateur resultat = service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class))
                .mettreAJourUtilisateur(1, new UpdateUtilisateurRequestDto(null, null, null, "690000001", "Yaoundé", null));

        assertEquals("Dupont", resultat.getNom()); // inchangé
        assertEquals("690000001", resultat.getTelephone());
        assertEquals("Yaoundé", resultat.getAdresse());
    }

    @Test
    void testMettreAJourUtilisateurReencodeLeNouveauMotDePasse() {
        // UT-18
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        PasswordEncoder encoder = Mockito.mock(PasswordEncoder.class);
        Utilisateur utilisateur = utilisateur(1, "jean@mail.com", StatutUtilisateur.actif);
        when(utilisateurs.findById(1)).thenReturn(Optional.of(utilisateur));
        when(utilisateurs.save(utilisateur)).thenReturn(utilisateur);
        when(encoder.encode("Nouveau1x")).thenReturn("$2a$10$autreHash");

        service(utilisateurs, Mockito.mock(EmailService.class), encoder)
                .mettreAJourUtilisateur(1, new UpdateUtilisateurRequestDto(null, null, null, null, null, "Nouveau1x"));

        assertEquals("$2a$10$autreHash", utilisateur.getMotPasse());
    }

    @Test
    void testMettreAJourUtilisateurSansAucunChampFourniNeLevePasDErreur() {
        // UT-V23 : le plan de test attend une ValidationException ("au moins un
        // champ doit être fourni"), mais l'implémentation actuelle ne porte pas
        // cette règle — un corps entièrement vide met simplement à jour dateMaj
        // sans rien changer d'autre. Ce test documente le comportement RÉEL ;
        // à faire évoluer si la règle du plan de test est effectivement retenue.
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        Utilisateur utilisateur = utilisateur(1, "jean@mail.com", StatutUtilisateur.actif);
        when(utilisateurs.findById(1)).thenReturn(Optional.of(utilisateur));
        when(utilisateurs.save(utilisateur)).thenReturn(utilisateur);

        Utilisateur resultat = service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class))
                .mettreAJourUtilisateur(1, new UpdateUtilisateurRequestDto(null, null, null, null, null, null));

        assertSame(utilisateur, resultat);
        verify(utilisateurs).save(utilisateur);
    }

    @Test
    void testMettreAJourUtilisateurRejetteEmailFormatInvalide() {
        // UT-V24
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        when(utilisateurs.findById(1)).thenReturn(Optional.of(utilisateur(1, "jean@mail.com", StatutUtilisateur.actif)));
        UtilisateurService service = service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class));

        assertThrows(ValidationException.class, () -> service.mettreAJourUtilisateur(1,
                new UpdateUtilisateurRequestDto(null, null, "pas-un-email", null, null, null)));
    }

    @Test
    void testMettreAJourUtilisateurRejetteEmailDejaUtiliseParAutreUtilisateur() {
        // UT-V25
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        Utilisateur utilisateur = utilisateur(1, "jean@mail.com", StatutUtilisateur.actif);
        Utilisateur autre = utilisateur(2, "paul@mail.com", StatutUtilisateur.actif);
        when(utilisateurs.findById(1)).thenReturn(Optional.of(utilisateur));
        when(utilisateurs.findByEmailIgnoreCase("paul@mail.com")).thenReturn(Optional.of(autre));
        UtilisateurService service = service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class));

        assertThrows(ValidationException.class, () -> service.mettreAJourUtilisateur(1,
                new UpdateUtilisateurRequestDto(null, null, "paul@mail.com", null, null, null)));
    }

    @Test
    void testMettreAJourUtilisateurAccepteSonPropreEmailInchange() {
        // Cas limite associé à UT-V25 : ne doit PAS se bloquer soi-même quand
        // l'email "déjà utilisé" trouvé est celui de l'utilisateur courant.
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        Utilisateur utilisateur = utilisateur(1, "jean@mail.com", StatutUtilisateur.actif);
        when(utilisateurs.findById(1)).thenReturn(Optional.of(utilisateur));
        when(utilisateurs.findByEmailIgnoreCase("jean@mail.com")).thenReturn(Optional.of(utilisateur));
        when(utilisateurs.save(utilisateur)).thenReturn(utilisateur);

        Utilisateur resultat = service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class))
                .mettreAJourUtilisateur(1, new UpdateUtilisateurRequestDto(null, null, "jean@mail.com", null, null, null));

        assertEquals("jean@mail.com", resultat.getEmail());
    }

    @Test
    void testMettreAJourUtilisateurRejetteMotDePasseFaible() {
        // UT-V26
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        when(utilisateurs.findById(1)).thenReturn(Optional.of(utilisateur(1, "jean@mail.com", StatutUtilisateur.actif)));
        UtilisateurService service = service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class));

        assertThrows(ValidationException.class, () -> service.mettreAJourUtilisateur(1,
                new UpdateUtilisateurRequestDto(null, null, null, null, null, "faible")));
    }

    // ------------------------------------------------------------------
    // connecterUtilisateur, deconnecterUtilisateur, lecture, suppression
    // ------------------------------------------------------------------

    @Test
    void testConnecterUtilisateur() {
        // ATTENTION : cette méthode n'est plus appelée par UtilisateurController
        // depuis que /connexion délègue entièrement à Spring Security
        // (AuthenticationManager + UtilisateurDetailsService + PasswordEncoder,
        // voir le commentaire de UtilisateurController#connecterUtilisateur).
        // Elle compare en plus le mot de passe EN CLAIR
        // (utilisateur.getMotPasse().equals(motPasse)), ce qui ne peut jamais
        // fonctionner puisque le mot de passe stocké est un hash BCrypt : ce
        // test documente ce code mort et potentiellement dangereux plutôt que
        // de masquer le problème. À supprimer, ou à corriger si un jour
        // réutilisée.
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        Utilisateur utilisateur = utilisateur(1, "jean@mail.com", StatutUtilisateur.actif);
        utilisateur.setMotPasse("$2a$10$hashBCrypt");
        when(utilisateurs.findByEmailIgnoreCase("jean@mail.com")).thenReturn(Optional.of(utilisateur));
        UtilisateurService service = service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class));

        // Le mot de passe en clair ne correspondra jamais au hash stocké.
        assertThrows(RuntimeException.class, () -> service.connecterUtilisateur("jean@mail.com", "motDePasseEnClair"));
    }

    @Test
    void testConnecterUtilisateurRejetteCompteVerrouille() {
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        Utilisateur utilisateur = utilisateur(1, "jean@mail.com", StatutUtilisateur.verrouille);
        when(utilisateurs.findByEmailIgnoreCase("jean@mail.com")).thenReturn(Optional.of(utilisateur));
        UtilisateurService service = service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class));

        assertThrows(RuntimeException.class, () -> service.connecterUtilisateur("jean@mail.com", "peu importe"));
    }

    @Test
    void testDeconnecterUtilisateur() {
        // UT-13
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        Utilisateur utilisateur = utilisateur(1, "jean@mail.com", StatutUtilisateur.actif);
        when(utilisateurs.findByEmailIgnoreCase("jean@mail.com")).thenReturn(Optional.of(utilisateur));

        service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class))
                .deconnecterUtilisateur("jean@mail.com");

        assertNotNull(utilisateur.getTokenValideDepuis());
        verify(utilisateurs).save(utilisateur);
    }

    @Test
    void testDeconnecterUtilisateurRejetteEmailInconnu() {
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        when(utilisateurs.findByEmailIgnoreCase("inconnu@mail.com")).thenReturn(Optional.empty());
        UtilisateurService service = service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class));
        assertThrows(UtilisateurNonTrouveException.class, () -> service.deconnecterUtilisateur("inconnu@mail.com"));
    }

    @Test
    void testRecupererUtilisateurParId() {
        // UT-15, UT-16
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        Utilisateur utilisateur = utilisateur(1, "jean@mail.com", StatutUtilisateur.actif);
        when(utilisateurs.findById(1)).thenReturn(Optional.of(utilisateur));
        assertSame(utilisateur, service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class))
                .recupererUtilisateurParId(1));

        when(utilisateurs.findById(99)).thenReturn(Optional.empty());
        UtilisateurService service = service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class));
        assertThrows(UtilisateurNonTrouveException.class, () -> service.recupererUtilisateurParId(99));
    }

    @Test
    void testRecupererUtilisateurParEmail() {
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        Utilisateur utilisateur = utilisateur(1, "jean@mail.com", StatutUtilisateur.actif);
        when(utilisateurs.findByEmailIgnoreCase("jean@mail.com")).thenReturn(Optional.of(utilisateur));
        assertSame(utilisateur, service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class))
                .recupererUtilisateurParEmail("jean@mail.com"));

        when(utilisateurs.findByEmailIgnoreCase("inconnu@mail.com")).thenReturn(Optional.empty());
        UtilisateurService service = service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class));
        assertThrows(UtilisateurNonTrouveException.class, () -> service.recupererUtilisateurParEmail("inconnu@mail.com"));
    }

    @Test
    void testSupprimerUtilisateur() {
        // UT-19
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        Utilisateur utilisateur = utilisateur(1, "jean@mail.com", StatutUtilisateur.actif);
        when(utilisateurs.findById(1)).thenReturn(Optional.of(utilisateur));

        service(utilisateurs, Mockito.mock(EmailService.class), Mockito.mock(PasswordEncoder.class)).supprimerUtilisateur(1);

        verify(utilisateurs).delete(utilisateur);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private UtilisateurService service(UtilisateurRepository utilisateurs, EmailService emailService, PasswordEncoder encoder) {
        return new UtilisateurService(utilisateurs, emailService, encoder);
    }

    private CreateUtilisateurRequestDTO dto(String nom, String prenom, String email, String telephone,
                                             String adresse, String motPasse) {
        return new CreateUtilisateurRequestDTO(nom, prenom, email, telephone, adresse, motPasse);
    }

    private Utilisateur utilisateur(int id, String email, StatutUtilisateur statut) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setId(id);
        utilisateur.setEmail(email);
        utilisateur.setStatut(statut);
        return utilisateur;
    }
}
