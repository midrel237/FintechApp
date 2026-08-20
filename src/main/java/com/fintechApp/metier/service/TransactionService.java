package com.fintechApp.metier.service;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fintechApp.metier.exception.RegleMetierException;
import com.fintechApp.metier.exception.RessourceIntrouvableException;
import com.fintechApp.metier.exception.SoldeInsuffisantException;
import com.fintechApp.metier.exception.TransactionNotFoundException;
import com.fintechApp.persistance.entity.Compte;
import com.fintechApp.persistance.entity.StatutCompte;
import com.fintechApp.persistance.entity.StatutTransaction;
import com.fintechApp.persistance.entity.Transaction;
import com.fintechApp.persistance.entity.Utilisateur;
import com.fintechApp.persistance.repository.CompteRepository;
import com.fintechApp.persistance.repository.TransactionRepository;
import com.fintechApp.persistance.repository.UtilisateurRepository;
import com.fintechApp.presentation.dto.transactionDTO.requestDTO.CreateTransactionRequestDTO;
import com.fintechApp.presentation.dto.transactionDTO.responseDTO.TransactionResponse;

import jakarta.transaction.Transactional;


@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CompteRepository compteRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final JournalComptableService journalComptableService;

    public TransactionService(TransactionRepository transactionRepository,
                               CompteRepository compteRepository,
                               UtilisateurRepository utilisateurRepository,
                               JournalComptableService journalComptableService) {
        this.transactionRepository = transactionRepository;
        this.compteRepository = compteRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.journalComptableService = journalComptableService;
    }

    // ------------------------------------------------------------------
    // 3.a) POST /v1/transactions/{idC} — créer une transaction
    // ------------------------------------------------------------------

    /**
     * Crée une transaction en statut en_attente.
     *
     * RG appliquées :
     * - Un utilisateur ne peut initier une transaction que si son statut
     *   est actif (sinon 403 UTILISATEUR_NON_ACTIF).
     * - Le compte source et le compte destination doivent exister
     *   (sinon 404 COMPTE_INTROUVABLE).
     * - idCompteDestination doit être différent de idCompteSource
     *   (sinon 422 COMPTES_IDENTIQUES).
     * - Un compte suspendu bloque toutes les transactions le concernant
     *   (sinon 409 COMPTE_SUSPENDU).
     * - La devise doit être identique entre compte source et compte
     *   destination (sinon 422 DEVISES_DIFFERENTES).
     * - Le montant doit être strictement positif (sinon 400 MONTANT_INVALIDE).
     * - Le transfert n'est possible que si le montant est inférieur ou égal
     *   au solde du compte source (sinon 422 SOLDE_INSUFFISANT).
     *
     * Le solde n'est débité/crédité et l'écriture comptable n'est générée
     * qu'à la confirmation ({@link #confirmerTransaction(Integer)}), pas à
     * la création.
     *
     * @param idCompteSource identifiant du compte source, porté par l'URI
     *                       /v1/transactions/{idC}
     * @param idUtilisateur  identifiant de l'utilisateur authentifié qui
     *                       initie la transaction (vérifié mais non
     *                       persisté : la table transaction ne porte pas de
     *                       colonne id_utilisateur)
     * @param requete        corps de la requête (compte destination,
     *                       montant, description)
     */
    @Transactional
    public TransactionResponse creerTransaction(Integer idCompteSource, Integer idUtilisateur,
                                                 CreateTransactionRequestDTO requete) {

        Utilisateur utilisateur = utilisateurRepository.findById(idUtilisateur)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "UTILISATEUR_INTROUVABLE", "L'utilisateur initiateur n'existe pas."));

        if (!utilisateur.estActif()) {
            throw new RegleMetierException("UTILISATEUR_NON_ACTIF",
                    "L'utilisateur doit être actif pour initier une transaction.", 403);
        }

        if (requete.getCompteDestination() == null) {
            throw new RegleMetierException("CHAMP_MANQUANT",
                    "Le compte de destination est requis.", 400);
        }

        if (requete.getMontant() == null || requete.getMontant().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegleMetierException("MONTANT_INVALIDE",
                    "Le montant doit être strictement positif.", 400);
        }

        if (idCompteSource.equals(requete.getCompteDestination())) {
            throw new RegleMetierException("COMPTES_IDENTIQUES",
                    "Le compte source et le compte destination doivent être différents.", 422);
        }

        Compte compteSource = compteRepository.findById(idCompteSource)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "COMPTE_INTROUVABLE", "Le compte source n'existe pas."));

        Compte compteDestination = compteRepository.findById(requete.getCompteDestination())
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "COMPTE_INTROUVABLE", "Le compte destination n'existe pas."));

        verifierComptesNonSuspendus(compteSource, compteDestination);

        if (!compteSource.getDevise().equals(compteDestination.getDevise())) {
            throw new RegleMetierException("DEVISES_DIFFERENTES",
                    "Le compte source et le compte destination doivent être dans la même devise.", 422);
        }

        if (requete.getMontant().compareTo(compteSource.getSolde()) > 0) {
            throw new SoldeInsuffisantException(compteSource.getIdCompte(), requete.getMontant(), compteSource.getSolde());
        }

        Transaction transaction = new Transaction();
        transaction.setReference(genererReference());
        transaction.setCompteSource(compteSource);
        transaction.setCompteDestination(compteDestination);
        transaction.setMontant(requete.getMontant());
        transaction.setDescription(requete.getDescription());
        transaction.setStatut(StatutTransaction.en_attente);
        transaction.setDateCreation(LocalDateTime.now());

        Transaction sauvegardee = transactionRepository.save(transaction);
        return TransactionResponse.save(sauvegardee);
    }

    // ------------------------------------------------------------------
    // 3.b) GET /v1/transactions/{idT} — lire une transaction
    // ------------------------------------------------------------------

    /**
     * Lit une transaction existante.
     *
     * @throws RessourceIntrouvableException (404) si la transaction n'existe pas
     */
    public TransactionResponse lireTransaction(Integer id) {
        Transaction transaction = trouverTransactionOuLeverErreur(id);
        return TransactionResponse.save(transaction);
    }

    // ------------------------------------------------------------------
    // 3.c) PATCH /v1/transactions/{idT} — confirmer une transaction
    // ------------------------------------------------------------------

    /**
     * Confirme une transaction en_attente : revérifie les conditions
     * susceptibles d'avoir changé depuis la création (comptes toujours
     * actifs, solde toujours suffisant), génère l'écriture comptable en
     * partie double (RG D — une seule ligne, débit + crédit, liée à la
     * transaction), puis dérive le nouveau solde des deux comptes à partir
     * du journal (RG15 : le solde est un champ calculé, jamais écrit
     * directement).
     *
     * @Transactional est essentiel ici : si une étape échoue (écriture du
     * journal, sauvegarde d'un compte...), toutes les modifications déjà
     * faites dans cette méthode sont annulées (rollback), aucune écriture
     * partielle ne subsiste en base.
     *
     * @throws TransactionNotFoundException (404) si la transaction n'existe pas
     * @throws RegleMetierException (409) si la transaction n'est plus en_attente
     * @throws RegleMetierException (409) si un des comptes a été suspendu entre-temps
     * @throws SoldeInsuffisantException (422) si le solde source est désormais insuffisant
     */
    @Transactional
    public TransactionResponse confirmerTransaction(Integer id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction introuvable avec l'ID : " + id));

        if (transaction.getStatut() != StatutTransaction.en_attente) {
            throw new RegleMetierException("TRANSACTION_NON_CONFIRMABLE",
                    "Seule une transaction en attente peut être confirmée (statut actuel : "
                            + transaction.getStatut() + ").", 409);
        }

        Compte compteSource = transaction.getCompteSource();
        Compte compteDestination = transaction.getCompteDestination();
        BigDecimal montant = transaction.getMontant();

        // RG : les contrôles (comptes, solde) sont revérifiés à la
        // confirmation, l'état ayant pu changer entre la création et la
        // confirmation de la transaction.
        verifierComptesNonSuspendus(compteSource, compteDestination);

        if (compteSource.getSolde().compareTo(montant) < 0) {
            throw new SoldeInsuffisantException(compteSource.getIdCompte(), montant, compteSource.getSolde());
        }

        // Écriture en partie double (une seule ligne, débit source + crédit
        // destination), liée à la transaction pour la traçabilité.
        var ligneJournal = journalComptableService.enregistrerVirement(
                compteSource, compteDestination, montant,
                "Virement " + transaction.getReference(), transaction);

        // Le solde stocké est recalculé depuis le journal (RG15), jamais
        // écrit directement à partir du montant du virement.
        compteSource.setSolde(journalComptableService.calculerSoldeDepuisJournal(compteSource.getIdCompte()));
        compteDestination.setSolde(journalComptableService.calculerSoldeDepuisJournal(compteDestination.getIdCompte()));

        transaction.setStatut(StatutTransaction.validee);
        transaction.setDateValidation(LocalDateTime.now());

        compteRepository.save(compteSource);
        compteRepository.save(compteDestination);
        Transaction transactionValidee = transactionRepository.save(transaction);

        TransactionResponse reponse = TransactionResponse.save(transactionValidee);
        reponse.setLigneJournalId(ligneJournal.getId());
        return reponse;
    }

    // ------------------------------------------------------------------
    // 3.d) PUT /v1/transactions/{idT}/annule — annuler une transaction
    // ------------------------------------------------------------------

    /**
     * Annule une transaction encore en_attente.
     *
     * RG appliquée : immuabilité comptable — une transaction déjà validee
     * (ou dans tout autre état final) ne peut être annulée ; seule une
     * contre-passation via le journal comptable est possible dans ce cas
     * (422 TRANSACTION_DEJA_VALIDEE).
     *
     * @param motif motif d'annulation (à des fins de traçabilité
     *              applicative ; non persisté, la table transaction ne
     *              porte pas de colonne dédiée dans le schéma SQL)
     */
    @Transactional
    public TransactionResponse annulerTransaction(Integer idT, String motif) {
        Transaction transaction = trouverTransactionOuLeverErreur(idT);

        if (transaction.getStatut() != StatutTransaction.en_attente) {
            throw new RegleMetierException("TRANSACTION_DEJA_VALIDEE",
                    "Une transaction déjà validée ne peut être annulée ; "
                            + "seule une contre-passation est possible.", 422);
        }

        transaction.setStatut(StatutTransaction.annulee);
        transaction.setDateAnnulation(LocalDateTime.now());

        Transaction transactionAnnulee = transactionRepository.save(transaction);
        return TransactionResponse.save(transactionAnnulee);
    }

    // ------------------------------------------------------------------
    // 3.e) PUT /v1/transactions/{idT}/suspend — suspendre une transaction
    // ------------------------------------------------------------------

    /**
     * Suspend une transaction qui n'est pas encore dans un état final.
     *
     * RG appliquée : une transaction validee, echouee ou annulee ne peut
     * plus être suspendue (422 TRANSACTION_DEJA_FINALE).
     */
    @Transactional
    public TransactionResponse suspendreTransaction(Integer id, String motif) {
        Transaction transaction = trouverTransactionOuLeverErreur(id);

        boolean etatFinal = transaction.getStatut() == StatutTransaction.validee
                || transaction.getStatut() == StatutTransaction.echouee
                || transaction.getStatut() == StatutTransaction.annulee;

        if (etatFinal) {
            throw new RegleMetierException("TRANSACTION_DEJA_FINALE",
                    "Une transaction validée, échouée ou annulée ne peut plus être suspendue.", 422);
        }

        transaction.setStatut(StatutTransaction.suspendue);
        transaction.setDateSuspension(LocalDateTime.now());

        Transaction transactionSuspendue = transactionRepository.save(transaction);
        return TransactionResponse.save(transactionSuspendue);
    }

    // ------------------------------------------------------------------
    // Méthodes utilitaires privées
    // ------------------------------------------------------------------

    private Transaction trouverTransactionOuLeverErreur(Integer id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "TRANSACTION_INTROUVABLE", "La transaction demandée n'existe pas."));
    }

    /**
     * RG : un compte suspendu bloque toutes les transactions le concernant.
     */
    private void verifierComptesNonSuspendus(Compte compteSource, Compte compteDestination) {
        if (compteSource.getStatut() == StatutCompte.SUSPENDU
                || compteDestination.getStatut() == StatutCompte.SUSPENDU) {
            throw new RegleMetierException("COMPTE_SUSPENDU",
                    "Un des comptes concernés par la transaction est suspendu.", 409);
        }
    }

    /**
     * Génère une référence de transaction unique (AN, unique en base selon
     * le dictionnaire de données Ref_T).
     */
    private String genererReference() {
        String reference;
        do {
            reference = "TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        } while (transactionRepository.existsByReference(reference));
        return reference;
    }
}
