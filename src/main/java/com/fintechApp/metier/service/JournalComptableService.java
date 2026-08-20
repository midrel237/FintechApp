package com.fintechApp.metier.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.fintechApp.metier.exception.CompteIntrouvableException;
import com.fintechApp.metier.exception.RegleMetierException;
import com.fintechApp.metier.exception.RessourceIntrouvableException;
import com.fintechApp.persistance.entity.Compte;
import com.fintechApp.persistance.entity.JournalComptable;
import com.fintechApp.persistance.entity.Transaction;
import com.fintechApp.persistance.repository.CompteRepository;
import com.fintechApp.persistance.repository.JournalComptableRepository;
import com.fintechApp.persistance.repository.TransactionRepository;
import com.fintechApp.presentation.dto.journalComptableDTO.requestDTO.JournalComptableRequestDTO;

import jakarta.persistence.criteria.Predicate;

/**
 * Service applicatif portant la logique du journal comptable (Partie 0, RG D) :
 * écritures en partie double, recalcul du solde à partir du journal (RG15,
 * RG32), immuabilité déléguée aux triggers SQL. Correspond au composant
 * "GestionnaireJournal" du diagramme de paquetages.
 *
 * Cas particulier RECHARGE / RETRAIT (RG : "NULL pour une recharge / un
 * retrait hors virement") : le schéma impose num_compte_debit ET
 * num_compte_credit NOT NULL sur chaque ligne (partie double stricte), or
 * une recharge/retrait ne concerne qu'un seul compte, sans contrepartie
 * interne. On modélise donc ces opérations comme des lignes où le MÊME
 * compte apparaît des deux côtés, avec un montant nul du côté qui ne bouge
 * pas (ex. recharge : montantCredit = montant, montantDebit = 0). Le solde
 * recalculé (crédits - débits) reste exact dans les deux cas.
 */
@Service
public class JournalComptableService {

    private final JournalComptableRepository journalComptableRepository;
    private final CompteRepository compteRepository;
    private final TransactionRepository transactionRepository;

    public JournalComptableService(JournalComptableRepository journalComptableRepository,
                                    CompteRepository compteRepository,
                                    TransactionRepository transactionRepository) {
        this.journalComptableRepository = journalComptableRepository;
        this.compteRepository = compteRepository;
        this.transactionRepository = transactionRepository;
    }

    /** Enregistre une ligne de crédit simple (recharge) sur un compte. */
    public void enregistrerCredit(Integer idCompte, BigDecimal montant, String libelle) {
        enregistrerMouvementSimple(idCompte, BigDecimal.ZERO, montant, libelle);
    }

    /** Enregistre une ligne de débit simple (retrait) sur un compte. */
    public void enregistrerDebit(Integer idCompte, BigDecimal montant, String libelle) {
        enregistrerMouvementSimple(idCompte, montant, BigDecimal.ZERO, libelle);
    }

    /**
     * Enregistre l'écriture en partie double d'un virement confirmé
     * (Partie 0, RG D) : UNE seule ligne de journal porte à la fois le
     * mouvement débit (compte source) et le mouvement crédit (compte
     * destination), avec le même montant des deux côtés, et référence la
     * transaction d'origine (id_transaction) pour la traçabilité.
     *
     * Appelée exclusivement par TransactionService#confirmerTransaction :
     * c'est cette écriture, et non une modification directe de compte.solde,
     * qui fait foi (RG15 : le solde est un champ calculé, jamais écrit
     * directement).
     */
    public JournalComptable enregistrerVirement(Compte compteSource, Compte compteDestination,
                                                 BigDecimal montant, String libelle, Transaction transaction) {
        JournalComptable ligne = new JournalComptable();
        ligne.setDateEnregistrement(LocalDateTime.now());
        ligne.setLibelle(libelle);
        ligne.setCompteDebit(compteSource);
        ligne.setCompteCredit(compteDestination);
        ligne.setMontantDebit(montant);
        ligne.setMontantCredit(montant);
        ligne.setTransaction(transaction);
        ligne.setLigneOrigine(null);
        ligne.setMotif(null);

        return journalComptableRepository.save(ligne);
    }

    private void enregistrerMouvementSimple(Integer idCompte, BigDecimal montantDebit,
                                             BigDecimal montantCredit, String libelle) {
        Compte compte = compteRepository.findById(idCompte)
                .orElseThrow(() -> new CompteIntrouvableException(idCompte));

        JournalComptable ligne = new JournalComptable();
        ligne.setDateEnregistrement(LocalDateTime.now());
        ligne.setLibelle(libelle);
        ligne.setCompteDebit(compte);
        ligne.setCompteCredit(compte);
        ligne.setMontantDebit(montantDebit);
        ligne.setMontantCredit(montantCredit);
        ligne.setTransaction(null);
        ligne.setLigneOrigine(null);
        ligne.setMotif(null);

        journalComptableRepository.save(ligne);
    }

    /**
     * Recalcule le solde d'un compte à partir de la somme de ses mouvements
     * dans le journal comptable (RG15 : le solde est un champ calculé,
     * jamais écrit directement).
     */
    public BigDecimal calculerSoldeDepuisJournal(Integer idCompte) {
        Compte compte = compteRepository.findById(idCompte)
                .orElseThrow(() -> new CompteIntrouvableException(idCompte));

        BigDecimal credits = journalComptableRepository.sommeCredits(compte);
        BigDecimal debits = journalComptableRepository.sommeDebits(compte);
        return credits.subtract(debits);
    }

    /**
     * RG32 : tout écart entre le solde stocké et le solde recalculé doit
     * déclencher une alerte de sécurité. Pas de système d'alerting externe
     * pour l'instant : trace au minimum dans les logs applicatifs.
     */
    public void declencherAlerteSecurite(Integer idCompte, BigDecimal ecart) {
        System.err.println("ALERTE SECURITE : incoherence de solde detectee sur le compte "
                + idCompte + " (ecart = " + ecart + ")");
    }

    // ------------------------------------------------------------------
    // 4.a) GET /api/journalComptable — lister les journaux (paginé)
    // ------------------------------------------------------------------

    /**
     * Liste les lignes du journal comptable, filtrables par dateDebut,
     * dateFin et idCompte (tous optionnels), et paginées.
     *
     * @throws RegleMetierException (400) si une date est mal formée ou si
     *                               dateDebut &gt; dateFin
     */
    public Page<JournalComptable> listerJournaux(String dateDebutBrut, String dateFinBrut,
                                                  Integer idCompte, int page, int taille) {
        LocalDateTime dateDebut = parserDateFiltre(dateDebutBrut, false);
        LocalDateTime dateFin = parserDateFiltre(dateFinBrut, true);

        if (dateDebut != null && dateFin != null && dateDebut.isAfter(dateFin)) {
            throw new RegleMetierException("PARAMETRE_INVALIDE",
                    "dateDebut doit être antérieure ou égale à dateFin.", 400);
        }

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(taille, 1),
                Sort.by(Sort.Direction.DESC, "dateEnregistrement"));
        return journalComptableRepository.findAll(construireSpecification(dateDebut, dateFin, idCompte), pageable);
    }

    /**
     * Construit dynamiquement les prédicats de filtrage : un filtre absent
     * (null) n'ajoute simplement aucun prédicat, au lieu d'être transmis à
     * la requête SQL sous forme de paramètre "? IS NULL" isolé — c'est ce
     * dernier pattern qui faisait échouer PostgreSQL avec "could not
     * determine data type of parameter $1" (SQLState 42P18), le driver ne
     * pouvant pas déduire le type d'un paramètre qui n'est jamais comparé à
     * une colonne typée.
     *
     * Le tri (date décroissante) est porté par le Pageable de l'appelant, et
     * non par un query.orderBy() ici : Spring Data JPA reconstruit toujours
     * l'ORDER BY à partir de pageable.getSort() après évaluation de la
     * Specification, donc un tri fixé ici serait silencieusement écrasé.
     */
    private Specification<JournalComptable> construireSpecification(LocalDateTime dateDebut,
                                                                      LocalDateTime dateFin,
                                                                      Integer idCompte) {
        return (root, query, cb) -> {
            List<Predicate> predicats = new ArrayList<>();

            if (dateDebut != null) {
                predicats.add(cb.greaterThanOrEqualTo(root.get("dateEnregistrement"), dateDebut));
            }
            if (dateFin != null) {
                predicats.add(cb.lessThanOrEqualTo(root.get("dateEnregistrement"), dateFin));
            }
            if (idCompte != null) {
                predicats.add(cb.or(
                        cb.equal(root.get("compteDebit").get("idCompte"), idCompte),
                        cb.equal(root.get("compteCredit").get("idCompte"), idCompte)));
            }

            return cb.and(predicats.toArray(new Predicate[0]));
        };
    }

    /**
     * Accepte indifféremment une date ("2026-01-31") ou un datetime ISO
     * ("2026-01-31T10:15:00") en filtre ; une date seule est ramenée au
     * début (00:00:00) ou à la fin (23:59:59) de la journée selon qu'elle
     * borne dateDebut ou dateFin, pour un filtrage inclusif intuitif.
     */
    private LocalDateTime parserDateFiltre(String valeur, boolean borneFin) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(valeur);
        } catch (DateTimeParseException premierEchec) {
            try {
                LocalDate date = LocalDate.parse(valeur);
                return borneFin ? date.atTime(23, 59, 59) : date.atStartOfDay();
            } catch (DateTimeParseException secondEchec) {
                throw new RegleMetierException("PARAMETRE_INVALIDE",
                        "Format de date invalide (attendu AAAA-MM-JJ ou AAAA-MM-JJTHH:mm:ss) : " + valeur, 400);
            }
        }
    }

    // ------------------------------------------------------------------
    // 4.b) GET /api/journalComptable/{idJ} — lire une ligne de journal
    // ------------------------------------------------------------------

    /**
     * @throws RessourceIntrouvableException (404) si idJ ne correspond à
     *                                        aucune ligne de journal
     */
    public JournalComptable lireJournal(Integer idJ) {
        return journalComptableRepository.findById(idJ)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "JOURNAL_INTROUVABLE", "La ligne de journal comptable demandée n'existe pas."));
    }

    // ------------------------------------------------------------------
    // 4.c) POST /api/journalComptable/{idJ}/nouveauJournal — contre-passer
    // ------------------------------------------------------------------

    /**
     * Corrige une ligne de journal existante en créant une nouvelle ligne
     * distincte qui la référence (RG : "aucune ligne ne peut être supprimée
     * ou modifiée" — immuabilité déléguée aux triggers SQL trg_journal_
     * comptable_no_update/no_delete, qui rejetteraient de toute façon un
     * UPDATE/DELETE direct).
     *
     * RG appliquées :
     * - la ligne d'origine (idJ) doit exister (sinon 404 JOURNAL_INTROUVABLE) ;
     * - numCompteDebit/numCompteCredit/montantDebit/montantCredit/refT sont
     *   requis (sinon 400 CHAMP_MANQUANT), tout comme le motif — mais le
     *   motif est vérifié séparément en 422 MOTIF_REQUIS car son absence est
     *   un manquement à la RG de traçabilité comptable, pas une simple
     *   erreur de syntaxe (conformément au contrat d'API, 4.c) ;
     * - montantDebit et montantCredit doivent être strictement positifs
     *   (sinon 400 MONTANT_INVALIDE) et égaux entre eux (sinon 422
     *   MONTANTS_DESEQUILIBRES — partie double stricte, Partie 0 §D) ;
     * - numCompteDebit, numCompteCredit et refT doivent référencer des
     *   ressources existantes (sinon 404).
     */
    public JournalComptable creerContrePassation(Integer idJ, JournalComptableRequestDTO requete) {
        JournalComptable ligneOrigine = lireJournal(idJ);

        if (requete.getNumCompteDebit() == null || requete.getNumCompteDebit().isBlank()
                || requete.getNumCompteCredit() == null || requete.getNumCompteCredit().isBlank()
                || requete.getMontantDebit() == null || requete.getMontantCredit() == null
                || requete.getRefT() == null || requete.getRefT().isBlank()) {
            throw new RegleMetierException("CHAMP_MANQUANT",
                    "numCompteDebit, numCompteCredit, montantDebit, montantCredit et refT sont requis.", 400);
        }

        if (requete.getMotif() == null || requete.getMotif().isBlank()) {
            throw new RegleMetierException("MOTIF_REQUIS",
                    "Une contre-passation doit être justifiée par un motif non vide.", 422);
        }

        if (requete.getMontantDebit().compareTo(BigDecimal.ZERO) <= 0
                || requete.getMontantCredit().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegleMetierException("MONTANT_INVALIDE",
                    "montantDebit et montantCredit doivent être strictement positifs.", 400);
        }

        if (requete.getMontantDebit().compareTo(requete.getMontantCredit()) != 0) {
            throw new RegleMetierException("MONTANTS_DESEQUILIBRES",
                    "Le montant débité doit être strictement égal au montant crédité (partie double).", 422);
        }

        Compte compteDebit = compteRepository.findByNumero(requete.getNumCompteDebit())
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "COMPTE_INTROUVABLE", "Le compte débité (numCompteDebit) n'existe pas."));

        Compte compteCredit = compteRepository.findByNumero(requete.getNumCompteCredit())
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "COMPTE_INTROUVABLE", "Le compte crédité (numCompteCredit) n'existe pas."));

        Transaction transaction = transactionRepository.findByReference(requete.getRefT())
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "TRANSACTION_INTROUVABLE", "Aucune transaction ne correspond à refT."));

        JournalComptable nouvelleLigne = new JournalComptable();
        nouvelleLigne.setDateEnregistrement(LocalDateTime.now());
        String libelle = (requete.getLibelleJ() != null && !requete.getLibelleJ().isBlank())
                ? requete.getLibelleJ()
                : "Contre-passation de la ligne #" + idJ;
        nouvelleLigne.setLibelle(libelle);
        nouvelleLigne.setCompteDebit(compteDebit);
        nouvelleLigne.setCompteCredit(compteCredit);
        nouvelleLigne.setMontantDebit(requete.getMontantDebit());
        nouvelleLigne.setMontantCredit(requete.getMontantCredit());
        nouvelleLigne.setTransaction(transaction);
        // idJ_nouveau doit contenir idJ_origine (règle de validation 4.c).
        nouvelleLigne.setLigneOrigine(ligneOrigine);
        nouvelleLigne.setMotif(requete.getMotif());

        JournalComptable ligneSauvegardee = journalComptableRepository.save(nouvelleLigne);

        // La contre-passation modifie les mouvements des comptes concernés :
        // leur solde (RG15, champ calculé) doit refléter ce nouveau mouvement.
        // (Set.of aurait levé IllegalArgumentException si compteDebit et
        // compteCredit désignaient le même compte ; on déduplique donc par id.)
        List<Compte> comptesAMettreAJour = compteDebit.getIdCompte().equals(compteCredit.getIdCompte())
                ? List.of(compteDebit)
                : List.of(compteDebit, compteCredit);
        for (Compte compte : comptesAMettreAJour) {
            compte.setSolde(calculerSoldeDepuisJournal(compte.getIdCompte()));
            compte.setDateMaj(LocalDateTime.now());
            compteRepository.save(compte);
        }

        return ligneSauvegardee;
    }
}
