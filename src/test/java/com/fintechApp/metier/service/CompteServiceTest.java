package com.fintechApp.metier.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mockito;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintechApp.metier.exception.CompteIntrouvableException;
import com.fintechApp.metier.exception.CompteNonVideException;
import com.fintechApp.metier.exception.CompteSuspenduException;
import com.fintechApp.metier.exception.IncoherenceSoldeException;
import com.fintechApp.metier.exception.RegleMetierException;
import com.fintechApp.metier.exception.SoldeInsuffisantException;
import com.fintechApp.metier.exception.UtilisateurIntrouvableException;
import com.fintechApp.metier.exception.UtilisateurNonActifException;
import com.fintechApp.metier.exception.ValidationException;
import com.fintechApp.persistance.entity.Compte;
import com.fintechApp.persistance.entity.StatutCompte;
import com.fintechApp.persistance.entity.StatutUtilisateur;
import com.fintechApp.persistance.entity.TypeCompte;
import com.fintechApp.persistance.entity.Utilisateur;
import com.fintechApp.persistance.repository.CompteRepository;
import com.fintechApp.persistance.repository.UtilisateurRepository;

class CompteServiceTest {

    @Test
    void testConsulterSolde() {
        Compte compte = compte(1, 10, "125.00", StatutCompte.ACTIF);
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        JournalComptableService journal = Mockito.mock(JournalComptableService.class);
        when(comptes.findById(1)).thenReturn(Optional.of(compte));
        when(journal.calculerSoldeDepuisJournal(1)).thenReturn(new BigDecimal("125.00"));

        assertEquals(new BigDecimal("125.00"), service(comptes, journal).consulterSolde(1, 10));
        verify(comptes, never()).save(compte);
    }

    @Test
    void testConsulterSoldeIncoherentSuspendLeCompteEtAlerte() {
        Compte compte = compte(1, 10, "100.00", StatutCompte.ACTIF);
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        JournalComptableService journal = Mockito.mock(JournalComptableService.class);
        when(comptes.findById(1)).thenReturn(Optional.of(compte));
        when(journal.calculerSoldeDepuisJournal(1)).thenReturn(new BigDecimal("75.00"));

        assertThrows(IncoherenceSoldeException.class,
                () -> service(comptes, journal).consulterSolde(1, 10));
        assertEquals(StatutCompte.SUSPENDU, compte.getStatut());
        verify(comptes).save(compte);
        verify(journal).declencherAlerteSecurite(1, new BigDecimal("-25.00"));
    }

    @Test
    void testCreerCompte() {
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        Utilisateur utilisateur = utilisateur(10, StatutUtilisateur.actif);
        when(utilisateurs.findById(10)).thenReturn(Optional.of(utilisateur));
        when(comptes.save(any(Compte.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Compte resultat = service(comptes, utilisateurs, Mockito.mock(JournalComptableService.class))
            .creerCompte(10, TypeCompte.COURANT, "EUR");

        assertSame(utilisateur, resultat.getIdUtilisateur());
        assertEquals(TypeCompte.COURANT, resultat.getType());
        assertEquals("EUR", resultat.getDevise());
        assertEquals(BigDecimal.ZERO, resultat.getSolde());
        assertEquals(StatutCompte.ACTIF, resultat.getStatut());
        assertNotNull(resultat.getNumero());
        assertNotNull(resultat.getDateCreation());
    }

        @Test
        void testCreerCompteRejetteUtilisateurAbsentInactifTypeEtDeviseInvalides() {
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        CompteService service = service(comptes, utilisateurs, Mockito.mock(JournalComptableService.class));
        when(utilisateurs.findById(10)).thenReturn(Optional.empty());
        assertThrows(UtilisateurIntrouvableException.class,
            () -> service.creerCompte(10, TypeCompte.COURANT, "EUR"));

        when(utilisateurs.findById(10)).thenReturn(Optional.of(utilisateur(10, StatutUtilisateur.verrouille)));
        assertThrows(UtilisateurNonActifException.class,
            () -> service.creerCompte(10, TypeCompte.COURANT, "EUR"));

        when(utilisateurs.findById(10)).thenReturn(Optional.of(utilisateur(10, StatutUtilisateur.actif)));
        assertThrows(ValidationException.class, () -> service.creerCompte(10, null, "EUR"));
        assertThrows(ValidationException.class, () -> service.creerCompte(10, TypeCompte.COURANT, "JPY"));
        }

    @Test
    void testLireCompte() {
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        Compte compte = compte(1, 10, "0", StatutCompte.ACTIF);
        when(comptes.findById(1)).thenReturn(Optional.of(compte));
        assertSame(compte, service(comptes, Mockito.mock(JournalComptableService.class)).lireCompte(1, 10));
    }

    @Test
    void testLireCompteRejetteCompteAbsentOuNonProprietaire() {
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        CompteService service = service(comptes, Mockito.mock(JournalComptableService.class));
        when(comptes.findById(1)).thenReturn(Optional.empty());
        assertThrows(CompteIntrouvableException.class, () -> service.lireCompte(1, 10));
        when(comptes.findById(1)).thenReturn(Optional.of(compte(1, 10, "0", StatutCompte.ACTIF)));
        assertThrows(RegleMetierException.class, () -> service.lireCompte(1, 99));
    }

    @Test
    void testListerComptesParUtilisateur() {
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        List<Compte> comptesAttendus = List.of(compte(1, 10, "0", StatutCompte.ACTIF));
        when(utilisateurs.existsById(10)).thenReturn(true);
        when(comptes.findByIdUtilisateur(10)).thenReturn(comptesAttendus);
        assertEquals(comptesAttendus,
            service(comptes, utilisateurs, Mockito.mock(JournalComptableService.class))
                .listerComptesParUtilisateur(10));
    }

        @Test
        void testListerComptesUtilisateurIntrouvable() {
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        when(utilisateurs.existsById(10)).thenReturn(false);
        assertThrows(UtilisateurIntrouvableException.class,
            () -> service(Mockito.mock(CompteRepository.class), utilisateurs,
                Mockito.mock(JournalComptableService.class)).listerComptesParUtilisateur(10));
        }

    @Test
    void testModifierCompte() {
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        Compte compte = compte(1, 10, "0", StatutCompte.ACTIF);
        when(comptes.findById(1)).thenReturn(Optional.of(compte));
        when(comptes.save(compte)).thenReturn(compte);
        Compte resultat = service(comptes, Mockito.mock(JournalComptableService.class))
                .modifierCompte(1, TypeCompte.EPARGNE, "USD", 10);
        assertSame(compte, resultat);
        assertEquals(TypeCompte.EPARGNE, compte.getType());
        assertEquals("USD", compte.getDevise());
        verify(comptes).save(compte);
    }

    @Test
    void testModifierCompteRejetteValeursNulles() {
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        when(comptes.findById(1)).thenReturn(Optional.of(compte(1, 10, "0", StatutCompte.ACTIF)));
        CompteService service = service(comptes, Mockito.mock(JournalComptableService.class));
        assertThrows(ValidationException.class, () -> service.modifierCompte(1, null, "EUR", 10));
        assertThrows(ValidationException.class,
                () -> service.modifierCompte(1, TypeCompte.COURANT, null, 10));
    }

    @Test
    void testRecharger() {
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        JournalComptableService journal = Mockito.mock(JournalComptableService.class);
        Compte compte = compte(1, 10, "0", StatutCompte.ACTIF);
        when(comptes.findById(1)).thenReturn(Optional.of(compte));
        when(journal.calculerSoldeDepuisJournal(1)).thenReturn(new BigDecimal("50.00"));
        when(comptes.save(compte)).thenReturn(compte);
        Compte resultat = service(comptes, journal).recharger(1, new BigDecimal("50.00"), 10);
        assertEquals(new BigDecimal("50.00"), resultat.getSolde());
        verify(journal).enregistrerCredit(1, new BigDecimal("50.00"), "Recharge du compte CPT-TEST");
        verify(comptes).save(compte);
    }

    @Test
    void testRechargerRejetteCompteSuspenduEtMontantInvalide() {
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        when(comptes.findById(1)).thenReturn(Optional.of(compte(1, 10, "0", StatutCompte.SUSPENDU)));
        CompteService service = service(comptes, Mockito.mock(JournalComptableService.class));
        assertThrows(CompteSuspenduException.class, () -> service.recharger(1, BigDecimal.ONE, 10));
        when(comptes.findById(1)).thenReturn(Optional.of(compte(1, 10, "0", StatutCompte.ACTIF)));
        assertThrows(ValidationException.class, () -> service.recharger(1, BigDecimal.ZERO, 10));
    }

    @Test
    void testRetirer() {
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        JournalComptableService journal = Mockito.mock(JournalComptableService.class);
        Compte compte = compte(1, 10, "100.00", StatutCompte.ACTIF);
        when(comptes.findById(1)).thenReturn(Optional.of(compte));
        when(journal.calculerSoldeDepuisJournal(1)).thenReturn(new BigDecimal("70.00"));
        when(comptes.save(compte)).thenReturn(compte);
        Compte resultat = service(comptes, journal).retirer(1, new BigDecimal("30.00"), 10);
        assertEquals(new BigDecimal("70.00"), resultat.getSolde());
        verify(journal).enregistrerDebit(1, new BigDecimal("30.00"), "Retrait du compte CPT-TEST");
        verify(comptes).save(compte);
    }

    @Test
    void testRetirerRejetteMontantCompteSuspenduEtSoldeInsuffisant() {
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        CompteService service = service(comptes, Mockito.mock(JournalComptableService.class));
        when(comptes.findById(1)).thenReturn(Optional.of(compte(1, 10, "20.00", StatutCompte.ACTIF)));
        assertThrows(ValidationException.class, () -> service.retirer(1, null, 10));
        assertThrows(SoldeInsuffisantException.class,
                () -> service.retirer(1, new BigDecimal("21.00"), 10));
        when(comptes.findById(1)).thenReturn(Optional.of(compte(1, 10, "20.00", StatutCompte.SUSPENDU)));
        assertThrows(CompteSuspenduException.class, () -> service.retirer(1, BigDecimal.ONE, 10));
    }

    @Test
    void testSupprimerCompte() {
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        when(comptes.findById(1)).thenReturn(Optional.of(compte(1, 10, "0", StatutCompte.ACTIF)));
        service(comptes, Mockito.mock(JournalComptableService.class)).supprimerCompte(1, 10);
        verify(comptes).deleteById(1);
    }

    @Test
    void testSupprimerCompteRejetteCompteSuspenduOuNonVide() {
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        CompteService service = service(comptes, Mockito.mock(JournalComptableService.class));
        when(comptes.findById(1)).thenReturn(Optional.of(compte(1, 10, "0", StatutCompte.SUSPENDU)));
        assertThrows(CompteSuspenduException.class, () -> service.supprimerCompte(1, 10));
        when(comptes.findById(1)).thenReturn(Optional.of(compte(1, 10, "1.00", StatutCompte.ACTIF)));
        assertThrows(CompteNonVideException.class, () -> service.supprimerCompte(1, 10));
    }

    private CompteService service(CompteRepository comptes, JournalComptableService journal) {
        return service(comptes, Mockito.mock(UtilisateurRepository.class), journal);
    }

    private CompteService service(CompteRepository comptes, UtilisateurRepository utilisateurs,
            JournalComptableService journal) {
        return new CompteService(comptes, utilisateurs, journal);
    }

    private Utilisateur utilisateur(int id, StatutUtilisateur statut) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setId(id);
        utilisateur.setStatut(statut);
        return utilisateur;
    }

    private Compte compte(int id, int utilisateurId, String solde, StatutCompte statut) {
        Compte compte = new Compte();
        compte.setIdCompte(id);
        compte.setNumero("CPT-TEST");
        compte.setIdUtilisateur(utilisateur(utilisateurId, StatutUtilisateur.actif));
        compte.setSolde(new BigDecimal(solde));
        compte.setStatut(statut);
        return compte;
    }
}
