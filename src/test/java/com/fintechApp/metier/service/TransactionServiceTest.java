package com.fintechApp.metier.service;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintechApp.metier.exception.RegleMetierException;
import com.fintechApp.metier.exception.RessourceIntrouvableException;
import com.fintechApp.metier.exception.SoldeInsuffisantException;
import com.fintechApp.metier.exception.TransactionNotFoundException;
import com.fintechApp.persistance.entity.Compte;
import com.fintechApp.persistance.entity.JournalComptable;
import com.fintechApp.persistance.entity.StatutCompte;
import com.fintechApp.persistance.entity.StatutTransaction;
import com.fintechApp.persistance.entity.StatutUtilisateur;
import com.fintechApp.persistance.entity.Transaction;
import com.fintechApp.persistance.entity.Utilisateur;
import com.fintechApp.persistance.repository.CompteRepository;
import com.fintechApp.persistance.repository.TransactionRepository;
import com.fintechApp.persistance.repository.UtilisateurRepository;
import com.fintechApp.presentation.dto.transactionDTO.requestDTO.CreateTransactionRequestDTO;
import com.fintechApp.presentation.dto.transactionDTO.responseDTO.TransactionResponse;

/**
 * Couvre le niveau "Tests unitaires" du plan de test pour le module
 * Transaction : cas TR-V01 à TR-V07, plus les cas fonctionnels de base
 * (création, lecture, confirmation, annulation, suspension).
 *
 * TR-V04 (idCompteSource non numérique dans l'URL) n'est PAS couverte ici :
 * ce cas est tranché par Spring MVC (MethodArgumentTypeMismatchException,
 * voir GlobalExceptionHandler) avant même que TransactionService ne soit
 * appelé — un test de service reçoit toujours un Integer déjà valide.
 *
 * TR-V06 (longueur maximale de la description) n'est pas non plus couverte :
 * le service actuel n'applique aucune limite de longueur sur ce champ. Ce
 * cas reste "à implémenter" plutôt que testé contre un comportement qui
 * n'existe pas encore.
 */
class TransactionServiceTest {

    // ------------------------------------------------------------------
    // creerTransaction — TR-01 à TR-09, TR-V01 à TR-V03, TR-V05, TR-V07
    // ------------------------------------------------------------------

    @Test
    void testCreerTransaction() {
        // TR-01
        Compte source = compte(1, 10, "100.00", "EUR", StatutCompte.ACTIF);
        Compte destination = compte(2, 20, "0.00", "EUR", StatutCompte.ACTIF);
        TransactionRepository transactions = Mockito.mock(TransactionRepository.class);
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        when(utilisateurs.findById(10)).thenReturn(Optional.of(utilisateur(10, StatutUtilisateur.actif)));
        when(comptes.findById(1)).thenReturn(Optional.of(source));
        when(comptes.findById(2)).thenReturn(Optional.of(destination));
        when(transactions.existsByReference(anyString())).thenReturn(false);
        when(transactions.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            t.setId(99);
            return t;
        });

        TransactionResponse resultat = service(transactions, comptes, utilisateurs)
                .creerTransaction(1, 10, requete(2, "30.00", "Loyer"));

        assertEquals("en_attente", resultat.getStatut());
        assertEquals(new BigDecimal("30.00"), resultat.getMontant());
        assertNotNull(resultat.getReference());
        assertEquals(new BigDecimal("100.00"), source.getSolde()); // TR-01 : aucun solde débité à la création
        verify(comptes, Mockito.never()).save(any(Compte.class));
    }

    @Test
    void testCreerTransactionAccepteDescriptionFacultative() {
        // TR-V05
        Compte source = compte(1, 10, "100.00", "EUR", StatutCompte.ACTIF);
        Compte destination = compte(2, 20, "0.00", "EUR", StatutCompte.ACTIF);
        TransactionRepository transactions = Mockito.mock(TransactionRepository.class);
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        when(utilisateurs.findById(10)).thenReturn(Optional.of(utilisateur(10, StatutUtilisateur.actif)));
        when(comptes.findById(1)).thenReturn(Optional.of(source));
        when(comptes.findById(2)).thenReturn(Optional.of(destination));
        when(transactions.existsByReference(anyString())).thenReturn(false);
        when(transactions.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse resultat = service(transactions, comptes, utilisateurs)
                .creerTransaction(1, 10, requete(2, "10.00", null));

        assertEquals(null, resultat.getDescription());
    }

    @Test
    void testCreerTransactionRejetteUtilisateurIntrouvableOuInactif() {
        // TR-03
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        TransactionService service = service(Mockito.mock(TransactionRepository.class),
                Mockito.mock(CompteRepository.class), utilisateurs);

        when(utilisateurs.findById(10)).thenReturn(Optional.empty());
        assertThrows(RessourceIntrouvableException.class,
                () -> service.creerTransaction(1, 10, requete(2, "10.00", null)));

        when(utilisateurs.findById(10)).thenReturn(Optional.of(utilisateur(10, StatutUtilisateur.verrouille)));
        assertThrows(RegleMetierException.class,
                () -> service.creerTransaction(1, 10, requete(2, "10.00", null)));
    }

    @Test
    void testCreerTransactionRejetteChampManquantEtMontantInvalide() {
        // TR-V01, TR-V02, TR-V03
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        when(utilisateurs.findById(10)).thenReturn(Optional.of(utilisateur(10, StatutUtilisateur.actif)));
        TransactionService service = service(Mockito.mock(TransactionRepository.class),
                Mockito.mock(CompteRepository.class), utilisateurs);

        assertThrows(RegleMetierException.class,
                () -> service.creerTransaction(1, 10, requete(null, "10.00", null))); // TR-V01
        assertThrows(RegleMetierException.class,
                () -> service.creerTransaction(1, 10, requete(2, null, null))); // TR-V02
        assertThrows(RegleMetierException.class,
                () -> service.creerTransaction(1, 10, requete(2, "0.00", null))); // TR-V03
        assertThrows(RegleMetierException.class,
                () -> service.creerTransaction(1, 10, requete(2, "-5.00", null))); // TR-V03
    }

    @Test
    void testCreerTransactionRejetteComptesIdentiques() {
        // TR-04
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        when(utilisateurs.findById(10)).thenReturn(Optional.of(utilisateur(10, StatutUtilisateur.actif)));
        TransactionService service = service(Mockito.mock(TransactionRepository.class),
                Mockito.mock(CompteRepository.class), utilisateurs);

        assertThrows(RegleMetierException.class,
                () -> service.creerTransaction(1, 10, requete(1, "10.00", null)));
    }

    @Test
    void testCreerTransactionRejetteCompteSourceOuDestinationIntrouvable() {
        // TR-05
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        when(utilisateurs.findById(10)).thenReturn(Optional.of(utilisateur(10, StatutUtilisateur.actif)));
        TransactionService service = service(Mockito.mock(TransactionRepository.class), comptes, utilisateurs);

        when(comptes.findById(1)).thenReturn(Optional.empty());
        assertThrows(RessourceIntrouvableException.class,
                () -> service.creerTransaction(1, 10, requete(2, "10.00", null)));

        when(comptes.findById(1)).thenReturn(Optional.of(compte(1, 10, "50.00", "EUR", StatutCompte.ACTIF)));
        when(comptes.findById(2)).thenReturn(Optional.empty());
        assertThrows(RessourceIntrouvableException.class,
                () -> service.creerTransaction(1, 10, requete(2, "10.00", null)));
    }

    @Test
    void testCreerTransactionRejetteAccesRefuseSiCompteSourceAppartientAUnAutreUtilisateur() {
        // TR-02 : non-régression de la faille IDOR corrigée
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        when(utilisateurs.findById(10)).thenReturn(Optional.of(utilisateur(10, StatutUtilisateur.actif)));
        when(comptes.findById(1)).thenReturn(Optional.of(compte(1, 99, "100.00", "EUR", StatutCompte.ACTIF)));
        TransactionService service = service(Mockito.mock(TransactionRepository.class), comptes, utilisateurs);

        RegleMetierException ex = assertThrows(RegleMetierException.class,
                () -> service.creerTransaction(1, 10, requete(2, "10.00", null)));
        assertEquals("ACCES_REFUSE", ex.getCode());
        assertEquals(403, ex.getHttpStatus());
    }

    @Test
    void testCreerTransactionRejetteCompteSuspendu() {
        // TR-06
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        when(utilisateurs.findById(10)).thenReturn(Optional.of(utilisateur(10, StatutUtilisateur.actif)));
        when(comptes.findById(1)).thenReturn(Optional.of(compte(1, 10, "100.00", "EUR", StatutCompte.SUSPENDU)));
        when(comptes.findById(2)).thenReturn(Optional.of(compte(2, 20, "0.00", "EUR", StatutCompte.ACTIF)));
        TransactionService service = service(Mockito.mock(TransactionRepository.class), comptes, utilisateurs);

        assertThrows(RegleMetierException.class,
                () -> service.creerTransaction(1, 10, requete(2, "10.00", null)));
    }

    @Test
    void testCreerTransactionRejetteDevisesDifferentes() {
        // TR-07
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        when(utilisateurs.findById(10)).thenReturn(Optional.of(utilisateur(10, StatutUtilisateur.actif)));
        when(comptes.findById(1)).thenReturn(Optional.of(compte(1, 10, "100.00", "EUR", StatutCompte.ACTIF)));
        when(comptes.findById(2)).thenReturn(Optional.of(compte(2, 20, "0.00", "USD", StatutCompte.ACTIF)));
        TransactionService service = service(Mockito.mock(TransactionRepository.class), comptes, utilisateurs);

        assertThrows(RegleMetierException.class,
                () -> service.creerTransaction(1, 10, requete(2, "10.00", null)));
    }

    @Test
    void testCreerTransactionRejetteSoldeInsuffisant() {
        // TR-09
        UtilisateurRepository utilisateurs = Mockito.mock(UtilisateurRepository.class);
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        when(utilisateurs.findById(10)).thenReturn(Optional.of(utilisateur(10, StatutUtilisateur.actif)));
        when(comptes.findById(1)).thenReturn(Optional.of(compte(1, 10, "5.00", "EUR", StatutCompte.ACTIF)));
        when(comptes.findById(2)).thenReturn(Optional.of(compte(2, 20, "0.00", "EUR", StatutCompte.ACTIF)));
        TransactionService service = service(Mockito.mock(TransactionRepository.class), comptes, utilisateurs);

        assertThrows(SoldeInsuffisantException.class,
                () -> service.creerTransaction(1, 10, requete(2, "10.00", null)));
    }

    // ------------------------------------------------------------------
    // lireTransaction — TR-10, TR-11
    // ------------------------------------------------------------------

    @Test
    void testLireTransaction() {
        TransactionRepository transactions = Mockito.mock(TransactionRepository.class);
        Transaction transaction = transaction(1, StatutTransaction.en_attente);
        when(transactions.findById(1)).thenReturn(Optional.of(transaction));

        TransactionResponse resultat = service(transactions, Mockito.mock(CompteRepository.class),
                Mockito.mock(UtilisateurRepository.class)).lireTransaction(1);

        assertEquals(1, resultat.getId());
    }

    @Test
    void testLireTransactionRejetteIdentifiantInexistant() {
        TransactionRepository transactions = Mockito.mock(TransactionRepository.class);
        when(transactions.findById(404)).thenReturn(Optional.empty());
        TransactionService service = service(transactions, Mockito.mock(CompteRepository.class),
                Mockito.mock(UtilisateurRepository.class));
        assertThrows(RessourceIntrouvableException.class, () -> service.lireTransaction(404));
    }

    // ------------------------------------------------------------------
    // confirmerTransaction — TR-12, TR-13, TR-14
    // ------------------------------------------------------------------

    @Test
    void testConfirmerTransaction() {
        // TR-12
        Compte source = compte(1, 10, "100.00", "EUR", StatutCompte.ACTIF);
        Compte destination = compte(2, 20, "0.00", "EUR", StatutCompte.ACTIF);
        Transaction transaction = transaction(1, StatutTransaction.en_attente);
        transaction.setCompteSource(source);
        transaction.setCompteDestination(destination);
        transaction.setMontant(new BigDecimal("30.00"));
        transaction.setReference("TXN-TEST");

        TransactionRepository transactions = Mockito.mock(TransactionRepository.class);
        CompteRepository comptes = Mockito.mock(CompteRepository.class);
        JournalComptableService journal = Mockito.mock(JournalComptableService.class);
        JournalComptable ligne = new JournalComptable();
        ligne.setId(555);
        when(transactions.findById(1)).thenReturn(Optional.of(transaction));
        when(journal.enregistrerVirement(source, destination, new BigDecimal("30.00"),
                "Virement TXN-TEST", transaction)).thenReturn(ligne);
        when(journal.calculerSoldeDepuisJournal(1)).thenReturn(new BigDecimal("70.00"));
        when(journal.calculerSoldeDepuisJournal(2)).thenReturn(new BigDecimal("30.00"));
        when(transactions.save(transaction)).thenReturn(transaction);

        TransactionResponse resultat = service(transactions, comptes, Mockito.mock(UtilisateurRepository.class), journal)
                .confirmerTransaction(1);

        assertEquals("validee", resultat.getStatut());
        assertEquals(Integer.valueOf(555), resultat.getLigneJournalId());
        assertEquals(new BigDecimal("70.00"), source.getSolde());
        assertEquals(new BigDecimal("30.00"), destination.getSolde());
        verify(comptes).save(source);
        verify(comptes).save(destination);
    }

    @Test
    void testConfirmerTransactionRejetteTransactionNonEnAttente() {
        // TR-13
        TransactionRepository transactions = Mockito.mock(TransactionRepository.class);
        when(transactions.findById(1)).thenReturn(Optional.of(transaction(1, StatutTransaction.validee)));
        TransactionService service = service(transactions, Mockito.mock(CompteRepository.class),
                Mockito.mock(UtilisateurRepository.class));
        assertThrows(RegleMetierException.class, () -> service.confirmerTransaction(1));
    }

    @Test
    void testConfirmerTransactionRejetteSoldeDevenuInsuffisant() {
        // TR-14 : le solde a pu diminuer entre la création et la confirmation
        Compte source = compte(1, 10, "5.00", "EUR", StatutCompte.ACTIF);
        Compte destination = compte(2, 20, "0.00", "EUR", StatutCompte.ACTIF);
        Transaction transaction = transaction(1, StatutTransaction.en_attente);
        transaction.setCompteSource(source);
        transaction.setCompteDestination(destination);
        transaction.setMontant(new BigDecimal("30.00"));

        TransactionRepository transactions = Mockito.mock(TransactionRepository.class);
        when(transactions.findById(1)).thenReturn(Optional.of(transaction));
        TransactionService service = service(transactions, Mockito.mock(CompteRepository.class),
                Mockito.mock(UtilisateurRepository.class));

        assertThrows(SoldeInsuffisantException.class, () -> service.confirmerTransaction(1));
    }

    @Test
    void testConfirmerTransactionRejetteCompteDevenuSuspendu() {
        Compte source = compte(1, 10, "100.00", "EUR", StatutCompte.SUSPENDU);
        Compte destination = compte(2, 20, "0.00", "EUR", StatutCompte.ACTIF);
        Transaction transaction = transaction(1, StatutTransaction.en_attente);
        transaction.setCompteSource(source);
        transaction.setCompteDestination(destination);
        transaction.setMontant(new BigDecimal("30.00"));

        TransactionRepository transactions = Mockito.mock(TransactionRepository.class);
        when(transactions.findById(1)).thenReturn(Optional.of(transaction));
        TransactionService service = service(transactions, Mockito.mock(CompteRepository.class),
                Mockito.mock(UtilisateurRepository.class));

        assertThrows(RegleMetierException.class, () -> service.confirmerTransaction(1));
    }

    @Test
    void testConfirmerTransactionRejetteIdentifiantInexistant() {
        TransactionRepository transactions = Mockito.mock(TransactionRepository.class);
        when(transactions.findById(404)).thenReturn(Optional.empty());
        TransactionService service = service(transactions, Mockito.mock(CompteRepository.class),
                Mockito.mock(UtilisateurRepository.class));
        assertThrows(TransactionNotFoundException.class, () -> service.confirmerTransaction(404));
    }

    // ------------------------------------------------------------------
    // annulerTransaction — TR-15, TR-16
    // ------------------------------------------------------------------

    @Test
    void testAnnulerTransaction() {
        // TR-15, TR-V07 (motif facultatif, non persisté)
        TransactionRepository transactions = Mockito.mock(TransactionRepository.class);
        Transaction transaction = transaction(1, StatutTransaction.en_attente);
        when(transactions.findById(1)).thenReturn(Optional.of(transaction));
        when(transactions.save(transaction)).thenReturn(transaction);

        TransactionResponse resultat = service(transactions, Mockito.mock(CompteRepository.class),
                Mockito.mock(UtilisateurRepository.class)).annulerTransaction(1, null);

        assertEquals("annulee", resultat.getStatut());
        assertNotNull(resultat.getDateAnnulation());
    }

    @Test
    void testAnnulerTransactionRejetteTransactionDejaValidee() {
        // TR-16
        TransactionRepository transactions = Mockito.mock(TransactionRepository.class);
        when(transactions.findById(1)).thenReturn(Optional.of(transaction(1, StatutTransaction.validee)));
        TransactionService service = service(transactions, Mockito.mock(CompteRepository.class),
                Mockito.mock(UtilisateurRepository.class));

        RegleMetierException ex = assertThrows(RegleMetierException.class, () -> service.annulerTransaction(1, "erreur de saisie"));
        assertEquals("TRANSACTION_DEJA_VALIDEE", ex.getCode());
        assertEquals(422, ex.getHttpStatus());
    }

    @Test
    void testAnnulerTransactionRejetteIdentifiantInexistant() {
        TransactionRepository transactions = Mockito.mock(TransactionRepository.class);
        when(transactions.findById(404)).thenReturn(Optional.empty());
        TransactionService service = service(transactions, Mockito.mock(CompteRepository.class),
                Mockito.mock(UtilisateurRepository.class));
        assertThrows(RessourceIntrouvableException.class, () -> service.annulerTransaction(404, null));
    }

    // ------------------------------------------------------------------
    // suspendreTransaction — TR-17
    // ------------------------------------------------------------------

    @Test
    void testSuspendreTransaction() {
        // TR-17
        TransactionRepository transactions = Mockito.mock(TransactionRepository.class);
        Transaction transaction = transaction(1, StatutTransaction.en_attente);
        when(transactions.findById(1)).thenReturn(Optional.of(transaction));
        when(transactions.save(transaction)).thenReturn(transaction);

        TransactionResponse resultat = service(transactions, Mockito.mock(CompteRepository.class),
                Mockito.mock(UtilisateurRepository.class)).suspendreTransaction(1, "suspicion de fraude");

        assertEquals("suspendue", resultat.getStatut());
        assertNotNull(resultat.getDateSuspension());
    }

    @Test
    void testSuspendreTransactionRejetteEtatFinal() {
        TransactionRepository transactions = Mockito.mock(TransactionRepository.class);
        TransactionService service = service(transactions, Mockito.mock(CompteRepository.class),
                Mockito.mock(UtilisateurRepository.class));

        for (StatutTransaction statut : new StatutTransaction[] {
                StatutTransaction.validee, StatutTransaction.echouee, StatutTransaction.annulee }) {
            when(transactions.findById(1)).thenReturn(Optional.of(transaction(1, statut)));
            assertThrows(RegleMetierException.class, () -> service.suspendreTransaction(1, "motif"),
                    "statut attendu rejeté : " + statut);
        }
    }

    @Test
    void testSuspendreTransactionRejetteIdentifiantInexistant() {
        TransactionRepository transactions = Mockito.mock(TransactionRepository.class);
        when(transactions.findById(404)).thenReturn(Optional.empty());
        TransactionService service = service(transactions, Mockito.mock(CompteRepository.class),
                Mockito.mock(UtilisateurRepository.class));
        assertThrows(RessourceIntrouvableException.class, () -> service.suspendreTransaction(404, null));
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private TransactionService service(TransactionRepository transactions, CompteRepository comptes,
                                        UtilisateurRepository utilisateurs) {
        return service(transactions, comptes, utilisateurs, Mockito.mock(JournalComptableService.class));
    }

    private TransactionService service(TransactionRepository transactions, CompteRepository comptes,
                                        UtilisateurRepository utilisateurs, JournalComptableService journal) {
        return new TransactionService(transactions, comptes, utilisateurs, journal);
    }

    private CreateTransactionRequestDTO requete(Integer compteDestination, String montant, String description) {
        return new CreateTransactionRequestDTO(compteDestination,
                montant != null ? new BigDecimal(montant) : null, description);
    }

    private Utilisateur utilisateur(int id, StatutUtilisateur statut) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setId(id);
        utilisateur.setStatut(statut);
        return utilisateur;
    }

    private Compte compte(int id, int utilisateurId, String solde, String devise, StatutCompte statut) {
        Compte compte = new Compte();
        compte.setIdCompte(id);
        compte.setNumero("CPT-" + id);
        compte.setIdUtilisateur(utilisateur(utilisateurId, StatutUtilisateur.actif));
        compte.setSolde(new BigDecimal(solde));
        compte.setDevise(devise);
        compte.setStatut(statut);
        return compte;
    }

    private Transaction transaction(int id, StatutTransaction statut) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setStatut(statut);
        transaction.setReference("TXN-" + id);
        return transaction;
    }

}
