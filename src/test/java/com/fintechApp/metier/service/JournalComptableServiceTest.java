package com.fintechApp.metier.service;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.fintechApp.metier.exception.CompteIntrouvableException;
import com.fintechApp.metier.exception.RegleMetierException;
import com.fintechApp.metier.exception.RessourceIntrouvableException;
import com.fintechApp.persistance.entity.Compte;
import com.fintechApp.persistance.entity.JournalComptable;
import com.fintechApp.persistance.entity.StatutUtilisateur;
import com.fintechApp.persistance.entity.Transaction;
import com.fintechApp.persistance.entity.Utilisateur;
import com.fintechApp.persistance.repository.CompteRepository;
import com.fintechApp.persistance.repository.JournalComptableRepository;
import com.fintechApp.persistance.repository.TransactionRepository;
import com.fintechApp.presentation.dto.journalComptableDTO.requestDTO.JournalComptableRequestDTO;

/**
 * Couvre le niveau "Tests unitaires" du plan de test pour le module Journal
 * comptable : cas JC-V01 à JC-V05, plus les cas fonctionnels de base.
 *
 * JC-V03 mérite une précision : le plan de test envisageait une erreur 400
 * pour une pagination hors limites (page=-1, taille=0). Le code réel ne lève
 * aucune erreur dans ce cas : il ramène silencieusement page et taille à des
 * valeurs par défaut (Math.max). Les tests ci-dessous vérifient ce
 * comportement RÉEL (repli silencieux), qui diffère de l'hypothèse initiale
 * du plan de test.
 */
class JournalComptableServiceTest {

    // ------------------------------------------------------------------
    // calculerSoldeDepuisJournal
    // ------------------------------------------------------------------

    @Test
    void testCalculerSoldeDepuisJournal() {
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        JournalComptableRepository journaux = Mockito.mock(JournalComptableRepository.class);
        Compte compte = compte(1);
        when(comptes.findById(1)).thenReturn(Optional.of(compte));
        when(journaux.sommeCredits(compte)).thenReturn(new BigDecimal("150.00"));
        when(journaux.sommeDebits(compte)).thenReturn(new BigDecimal("40.00"));

        BigDecimal solde = service(journaux, comptes, Mockito.mock(TransactionRepository.class))
                .calculerSoldeDepuisJournal(1);

        assertEquals(new BigDecimal("110.00"), solde);
    }

    @Test
    void testCalculerSoldeDepuisJournalRejetteCompteIntrouvable() {
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        when(comptes.findById(404)).thenReturn(Optional.empty());
        JournalComptableService service = service(Mockito.mock(JournalComptableRepository.class), comptes,
                Mockito.mock(TransactionRepository.class));
        assertThrows(CompteIntrouvableException.class, () -> service.calculerSoldeDepuisJournal(404));
    }

    // ------------------------------------------------------------------
    // enregistrerCredit / enregistrerDebit / enregistrerVirement
    // ------------------------------------------------------------------

    @Test
    void testEnregistrerCredit() {
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        JournalComptableRepository journaux = Mockito.mock(JournalComptableRepository.class);
        Compte compte = compte(1);
        when(comptes.findById(1)).thenReturn(Optional.of(compte));
        ArgumentCaptor<JournalComptable> captor = ArgumentCaptor.forClass(JournalComptable.class);

        service(journaux, comptes, Mockito.mock(TransactionRepository.class))
                .enregistrerCredit(1, new BigDecimal("50.00"), "Recharge");

        verify(journaux).save(captor.capture());
        assertEquals(BigDecimal.ZERO, captor.getValue().getMontantDebit());
        assertEquals(new BigDecimal("50.00"), captor.getValue().getMontantCredit());
        assertEquals(compte, captor.getValue().getCompteDebit());
        assertEquals(compte, captor.getValue().getCompteCredit());
    }

    @Test
    void testEnregistrerDebit() {
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        JournalComptableRepository journaux = Mockito.mock(JournalComptableRepository.class);
        Compte compte = compte(1);
        when(comptes.findById(1)).thenReturn(Optional.of(compte));
        ArgumentCaptor<JournalComptable> captor = ArgumentCaptor.forClass(JournalComptable.class);

        service(journaux, comptes, Mockito.mock(TransactionRepository.class))
                .enregistrerDebit(1, new BigDecimal("20.00"), "Retrait");

        verify(journaux).save(captor.capture());
        assertEquals(new BigDecimal("20.00"), captor.getValue().getMontantDebit());
        assertEquals(BigDecimal.ZERO, captor.getValue().getMontantCredit());
    }

    @Test
    void testEnregistrerCreditRejetteCompteIntrouvable() {
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        when(comptes.findById(404)).thenReturn(Optional.empty());
        JournalComptableService service = service(Mockito.mock(JournalComptableRepository.class), comptes,
                Mockito.mock(TransactionRepository.class));
        assertThrows(CompteIntrouvableException.class,
                () -> service.enregistrerCredit(404, BigDecimal.TEN, "Recharge"));
    }

    @Test
    void testEnregistrerVirement() {
        // Écriture en partie double : une seule ligne, débit source + crédit destination
        JournalComptableRepository journaux = Mockito.mock(JournalComptableRepository.class);
        Compte source = compte(1);
        Compte destination = compte(2);
        Transaction transaction = new Transaction();
        transaction.setId(9);
        when(journaux.save(any(JournalComptable.class))).thenAnswer(inv -> inv.getArgument(0));

        JournalComptable resultat = service(journaux, Mockito.mock(CompteRepository.class),
                Mockito.mock(TransactionRepository.class))
                .enregistrerVirement(source, destination, new BigDecimal("30.00"), "Virement TXN-1", transaction);

        assertEquals(source, resultat.getCompteDebit());
        assertEquals(destination, resultat.getCompteCredit());
        assertEquals(new BigDecimal("30.00"), resultat.getMontantDebit());
        assertEquals(new BigDecimal("30.00"), resultat.getMontantCredit());
        assertEquals(transaction, resultat.getTransaction());
    }

    // ------------------------------------------------------------------
    // declencherAlerteSecurite — RG32
    // ------------------------------------------------------------------

    @Test
    void testDeclencherAlerteSecuriteNeLevePasDException() {
        JournalComptableService service = service(Mockito.mock(JournalComptableRepository.class),
                Mockito.mock(CompteRepository.class), Mockito.mock(TransactionRepository.class));
        assertDoesNotThrow(() -> service.declencherAlerteSecurite(1, new BigDecimal("-25.00")));
    }

    // ------------------------------------------------------------------
    // lireJournal — JC-05
    // ------------------------------------------------------------------

    @Test
    void testLireJournal() {
        JournalComptableRepository journaux = Mockito.mock(JournalComptableRepository.class);
        JournalComptable ligne = new JournalComptable();
        ligne.setId(1);
        when(journaux.findById(1)).thenReturn(Optional.of(ligne));

        assertEquals(ligne, service(journaux, Mockito.mock(CompteRepository.class),
                Mockito.mock(TransactionRepository.class)).lireJournal(1));
    }

    @Test
    void testLireJournalRejetteIdentifiantInexistant() {
        // JC-05
        JournalComptableRepository journaux = Mockito.mock(JournalComptableRepository.class);
        when(journaux.findById(404)).thenReturn(Optional.empty());
        JournalComptableService service = service(journaux, Mockito.mock(CompteRepository.class),
                Mockito.mock(TransactionRepository.class));
        assertThrows(RessourceIntrouvableException.class, () -> service.lireJournal(404));
    }

    // ------------------------------------------------------------------
    // listerJournaux — JC-V01, JC-V02, JC-V03
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void testListerJournauxAccepteDateSeuleEtDateHeureISO() {
        JournalComptableRepository journaux = Mockito.mock(JournalComptableRepository.class);
        when(journaux.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of()));
        JournalComptableService service = service(journaux, Mockito.mock(CompteRepository.class),
                Mockito.mock(TransactionRepository.class));

        assertDoesNotThrow(() -> service.listerJournaux("2026-08-07", null, null, 0, 20));
        assertDoesNotThrow(() -> service.listerJournaux(null, "2026-08-07T10:15:00", null, 0, 20));
    }

    @Test
    void testListerJournauxRejetteFormatDateInvalide() {
        // JC-V01
        JournalComptableService service = service(Mockito.mock(JournalComptableRepository.class),
                Mockito.mock(CompteRepository.class), Mockito.mock(TransactionRepository.class));

        RegleMetierException ex = assertThrows(RegleMetierException.class,
                () -> service.listerJournaux("07-08-2026", null, null, 0, 20));
        assertEquals("PARAMETRE_INVALIDE", ex.getCode());
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    void testListerJournauxRejetteDateDebutApresDateFin() {
        // JC-V02
        JournalComptableService service = service(Mockito.mock(JournalComptableRepository.class),
                Mockito.mock(CompteRepository.class), Mockito.mock(TransactionRepository.class));

        assertThrows(RegleMetierException.class,
                () -> service.listerJournaux("2026-08-10", "2026-08-01", null, 0, 20));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListerJournauxRamenePaginationHorsLimitesAuxValeursParDefaut() {
        // JC-V03 : comportement réel du code (Math.max), différent de
        // l'hypothèse initiale du plan de test (voir commentaire de la classe).
        JournalComptableRepository journaux = Mockito.mock(JournalComptableRepository.class);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(journaux.findAll(any(Specification.class), captor.capture()))
                .thenReturn(new PageImpl<>(java.util.List.of()));
        JournalComptableService service = service(journaux, Mockito.mock(CompteRepository.class),
                Mockito.mock(TransactionRepository.class));

        service.listerJournaux(null, null, null, -1, 0);

        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(1, captor.getValue().getPageSize());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListerJournauxFiltreParCompte() {
        JournalComptableRepository journaux = Mockito.mock(JournalComptableRepository.class);
        when(journaux.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of()));
        JournalComptableService service = service(journaux, Mockito.mock(CompteRepository.class),
                Mockito.mock(TransactionRepository.class));

        Page<JournalComptable> resultat = service.listerJournaux(null, null, 7, 0, 20);

        assertNotNull(resultat);
        verify(journaux).findAll(any(Specification.class), any(Pageable.class));
    }

    // ------------------------------------------------------------------
    // creerContrePassation — JC-06, JC-V04, JC-V05
    // ------------------------------------------------------------------

    @Test
    void testCreerContrePassation() {
        // JC-06
        JournalComptableRepository journaux = Mockito.mock(JournalComptableRepository.class);
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        TransactionRepository transactions = Mockito.mock(TransactionRepository.class);
        JournalComptable ligneOrigine = new JournalComptable();
        ligneOrigine.setId(10);
        Compte compteDebit = compte(1);
        Compte compteCredit = compte(2);
        Transaction transaction = new Transaction();
        transaction.setReference("TXN-1");
        when(journaux.findById(10)).thenReturn(Optional.of(ligneOrigine));
        when(comptes.findByNumero("CPT-1")).thenReturn(Optional.of(compteDebit));
        when(comptes.findByNumero("CPT-2")).thenReturn(Optional.of(compteCredit));
        when(transactions.findByReference("TXN-1")).thenReturn(Optional.of(transaction));
        when(journaux.save(any(JournalComptable.class))).thenAnswer(inv -> inv.getArgument(0));
        when(journaux.sommeCredits(any(Compte.class))).thenReturn(BigDecimal.ZERO);
        when(journaux.sommeDebits(any(Compte.class))).thenReturn(BigDecimal.ZERO);

        JournalComptableRequestDTO requete = new JournalComptableRequestDTO(
                "CPT-1", "CPT-2", new BigDecimal("15.00"), new BigDecimal("15.00"),
                "Correction", "TXN-1", "Erreur de saisie initiale");

        JournalComptable resultat = service(journaux, comptes, transactions).creerContrePassation(10, requete);

        assertEquals(ligneOrigine, resultat.getLigneOrigine()); // idJ_nouveau référence idJ_origine
        assertEquals("Erreur de saisie initiale", resultat.getMotif());
        verify(comptes).save(compteDebit);
        verify(comptes).save(compteCredit);
    }

    @Test
    void testCreerContrePassationRejetteChampsManquants() {
        JournalComptableRepository journaux = Mockito.mock(JournalComptableRepository.class);
        when(journaux.findById(10)).thenReturn(Optional.of(new JournalComptable()));
        JournalComptableService service = service(journaux, Mockito.mock(CompteRepository.class),
                Mockito.mock(TransactionRepository.class));

        RegleMetierException ex = assertThrows(RegleMetierException.class, () -> service.creerContrePassation(10,
                new JournalComptableRequestDTO(null, "CPT-2", new BigDecimal("10"), new BigDecimal("10"),
                        null, "TXN-1", "motif")));
        assertEquals("CHAMP_MANQUANT", ex.getCode());
    }

    @Test
    void testCreerContrePassationRejetteMotifManquant() {
        JournalComptableRepository journaux = Mockito.mock(JournalComptableRepository.class);
        when(journaux.findById(10)).thenReturn(Optional.of(new JournalComptable()));
        JournalComptableService service = service(journaux, Mockito.mock(CompteRepository.class),
                Mockito.mock(TransactionRepository.class));

        RegleMetierException ex = assertThrows(RegleMetierException.class, () -> service.creerContrePassation(10,
                new JournalComptableRequestDTO("CPT-1", "CPT-2", new BigDecimal("10"), new BigDecimal("10"),
                        null, "TXN-1", " ")));
        assertEquals("MOTIF_REQUIS", ex.getCode());
        assertEquals(422, ex.getHttpStatus());
    }

    @Test
    void testCreerContrePassationRejetteMontantInvalideOuDesequilibre() {
        // JC-V04
        JournalComptableRepository journaux = Mockito.mock(JournalComptableRepository.class);
        when(journaux.findById(10)).thenReturn(Optional.of(new JournalComptable()));
        JournalComptableService service = service(journaux, Mockito.mock(CompteRepository.class),
                Mockito.mock(TransactionRepository.class));

        assertThrows(RegleMetierException.class, () -> service.creerContrePassation(10,
                new JournalComptableRequestDTO("CPT-1", "CPT-2", BigDecimal.ZERO, new BigDecimal("10"),
                        null, "TXN-1", "motif")));

        RegleMetierException desequilibre = assertThrows(RegleMetierException.class, () -> service.creerContrePassation(10,
                new JournalComptableRequestDTO("CPT-1", "CPT-2", new BigDecimal("10"), new BigDecimal("15"),
                        null, "TXN-1", "motif")));
        assertEquals("MONTANTS_DESEQUILIBRES", desequilibre.getCode());
        assertEquals(422, desequilibre.getHttpStatus());
    }

    @Test
    void testCreerContrePassationRejetteLigneOrigineIntrouvable() {
        // JC-V05
        JournalComptableRepository journaux = Mockito.mock(JournalComptableRepository.class);
        when(journaux.findById(404)).thenReturn(Optional.empty());
        JournalComptableService service = service(journaux, Mockito.mock(CompteRepository.class),
                Mockito.mock(TransactionRepository.class));

        assertThrows(RessourceIntrouvableException.class, () -> service.creerContrePassation(404,
                new JournalComptableRequestDTO("CPT-1", "CPT-2", new BigDecimal("10"), new BigDecimal("10"),
                        null, "TXN-1", "motif")));
    }

    @Test
    void testCreerContrePassationRejetteComptesOuTransactionIntrouvables() {
        JournalComptableRepository journaux = Mockito.mock(JournalComptableRepository.class);
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        TransactionRepository transactions = Mockito.mock(TransactionRepository.class);
        when(journaux.findById(10)).thenReturn(Optional.of(new JournalComptable()));
        JournalComptableService service = service(journaux, comptes, transactions);
        JournalComptableRequestDTO requete = new JournalComptableRequestDTO(
                "CPT-1", "CPT-2", new BigDecimal("10"), new BigDecimal("10"), null, "TXN-1", "motif");

        when(comptes.findByNumero("CPT-1")).thenReturn(Optional.empty());
        assertThrows(RessourceIntrouvableException.class, () -> service.creerContrePassation(10, requete));

        when(comptes.findByNumero("CPT-1")).thenReturn(Optional.of(compte(1)));
        when(comptes.findByNumero("CPT-2")).thenReturn(Optional.empty());
        assertThrows(RessourceIntrouvableException.class, () -> service.creerContrePassation(10, requete));

        when(comptes.findByNumero("CPT-2")).thenReturn(Optional.of(compte(2)));
        when(transactions.findByReference("TXN-1")).thenReturn(Optional.empty());
        assertThrows(RessourceIntrouvableException.class, () -> service.creerContrePassation(10, requete));
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private JournalComptableService service(JournalComptableRepository journaux, CompteRepository comptes,
                                              TransactionRepository transactions) {
        return new JournalComptableService(journaux, comptes, transactions);
    }

    private Compte compte(int id) {
        Compte compte = new Compte();
        compte.setIdCompte(id);
        compte.setNumero("CPT-" + id);
        compte.setIdUtilisateur(utilisateur(10));
        compte.setSolde(BigDecimal.ZERO);
        return compte;
    }

    private Utilisateur utilisateur(int id) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setId(id);
        utilisateur.setStatut(StatutUtilisateur.actif);
        return utilisateur;
    }
}
