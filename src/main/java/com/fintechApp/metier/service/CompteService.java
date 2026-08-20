package com.fintechApp.metier.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fintechApp.metier.exception.CompteIntrouvableException;
import com.fintechApp.metier.exception.CompteNonVideException;
import com.fintechApp.metier.exception.CompteSuspenduException;
import com.fintechApp.metier.exception.IncoherenceSoldeException;
import com.fintechApp.metier.exception.SoldeInsuffisantException;
import com.fintechApp.metier.exception.UtilisateurIntrouvableException;
import com.fintechApp.metier.exception.UtilisateurNonActifException;
import com.fintechApp.metier.exception.ValidationException;
import com.fintechApp.persistance.entity.Compte;
import com.fintechApp.persistance.entity.StatutCompte;
import com.fintechApp.persistance.entity.TypeCompte;
import com.fintechApp.persistance.entity.Utilisateur;
import com.fintechApp.persistance.repository.CompteRepository;
import com.fintechApp.persistance.repository.UtilisateurRepository;

import org.springframework.stereotype.Service;


/**
 * Service applicatif portant l'orchestration des cas d'utilisation liés au Compte.
 * Correspond au composant "GestionnaireCompte" de la couche Application décrite
 * dans le diagramme de paquetages (Partie 0, III) : il coordonne la couche Métier
 * (règles de gestion) et la couche Persistance (repositories), mais ne contient
 * lui-même aucune règle de gestion qu'il ne ferait pas appliquer par le domaine.
 *
 * Règles de gestion couvertes (Partie 0, §1.B et §1.D) :
 * - RG12 : un compte est créé par un et un seul utilisateur actif.
 * - RG13 : attributs d'un compte (id, numéro unique, type, solde, devise, dates).
 * - RG14 : un compte est configuré pour une seule devise, fixée à la création.
 * - RG15 : le solde n'est jamais modifiable directement, c'est un champ calculé.
 * - RG16 : un compte est de type EPARGNE ou COURANT.
 * - RG17 : un compte suspendu bloque toutes les transactions le concernant.
 * - RG32 : le solde stocké doit toujours correspondre à la somme des mouvements
 *          du journal comptable ; tout écart bloque le compte et déclenche une alerte.
 */
@Service
public class CompteService {

    /** Devises supportées par la plateforme (RG14). À externaliser en configuration si besoin. */
    private static final Set<String> DEVISES_SUPPORTEES = Set.of("EUR", "USD", "XAF", "GBP");

    private final CompteRepository compteRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final JournalComptableService journalComptableService;

    public CompteService(CompteRepository compteRepository,
                          UtilisateurRepository utilisateurRepository,
                          JournalComptableService journalComptableService) {
        this.compteRepository = compteRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.journalComptableService = journalComptableService;
    }

    /**
     * Crée un compte bancaire pour un utilisateur actif (RG12, RG13, RG14, RG15, RG16).
     * Correspond à l'endpoint POST /v1/accounts.
     *
     * @param idUtilisateur identifiant du propriétaire du compte
     * @param typeCompte    EPARGNE ou COURANT
     * @param devise        code devise ISO 4217, fixé définitivement pour ce compte
     * @return le compte créé, avec un solde initial à zéro
     * @throws UtilisateurIntrouvableException si idUtilisateur ne correspond à aucun utilisateur
     * @throws UtilisateurNonActifException    si l'utilisateur n'est pas au statut ACTIF
     * @throws ValidationException             si typeCompte ou devise est absent/invalide
     */
    public Compte creerCompte(Integer idUtilisateur, TypeCompte typeCompte, String devise) {
        Utilisateur utilisateur = utilisateurRepository.findById(idUtilisateur)
                .orElseThrow(() -> new UtilisateurIntrouvableException(idUtilisateur));

        if (!utilisateur.estActif()) {
            throw new UtilisateurNonActifException(idUtilisateur);
        }

        if (typeCompte == null) {
            throw new ValidationException("typeCompte");
        }
        validerDevise(devise);

        Compte compte = new Compte();
        compte.setNumero(genererNumeroCompte());
        compte.setIdUtilisateur(utilisateur);
        compte.setType(typeCompte);
        compte.setDevise(devise);
        compte.setSolde(BigDecimal.ZERO); // RG15 : champ calculé, initialisé à zéro
        compte.setStatut(StatutCompte.ACTIF);
        compte.setDateCreation(LocalDateTime.now());
        compte.setDateMaj(LocalDateTime.now());

        return compteRepository.save(compte);
    }

    /**
     * Lit un compte par son identifiant.
     * Correspond à l'endpoint GET /v1/accounts/{idC}.
     *
     * @throws CompteIntrouvableException si idCompte ne correspond à aucun compte
     */
    public Compte lireCompte(Integer idCompte) {
        return compteRepository.findById(idCompte)
                .orElseThrow(() -> new CompteIntrouvableException(idCompte));
    }

    /**
     * Liste l'ensemble des comptes détenus par un utilisateur (RG8 : un utilisateur
     * peut posséder un ou plusieurs comptes).
     */
    public List<Compte> listerComptesParUtilisateur(Integer idUtilisateur) {
        if (!utilisateurRepository.existsById(idUtilisateur)) {
            throw new UtilisateurIntrouvableException(idUtilisateur);
        }
        return compteRepository.findByIdUtilisateur(idUtilisateur);
    }

    /**
     * Modifie les informations non calculées d'un compte (actuellement : son type).
     * Le solde et la devise ne sont volontairement pas modifiables ici (RG14, RG15).
     * Correspond à l'endpoint PUT /v1/accounts/{idC}.
     *
     * @throws CompteIntrouvableException si idCompte ne correspond à aucun compte
     * @throws ValidationException        si nouveauType est absent
     */
    public Compte modifierCompte(Integer idCompte, TypeCompte nouveauType) {
        Compte compte = lireCompte(idCompte);

        if (nouveauType == null) {
            throw new ValidationException("typeCompte");
        }

        compte.setType(nouveauType);
        compte.setDateMaj(LocalDateTime.now());

        return compteRepository.save(compte);
    }

    /**
     * Supprime un compte bancaire.
     * Correspond à l'endpoint DELETE /v1/accounts/{idC}.
     *
     * Règle métier (contrat d'API, 2.d) : la suppression n'est possible que si
     * le solde est nul et le compte n'est pas suspendu.
     *
     * @throws CompteIntrouvableException si idCompte ne correspond à aucun compte
     * @throws CompteNonVideException     si le solde du compte est différent de zéro
     * @throws CompteSuspenduException    si le compte est suspendu
     */
    public void supprimerCompte(Integer idCompte) {
        Compte compte = lireCompte(idCompte);

        if (compte.getStatut() == StatutCompte.SUSPENDU) {
            throw new CompteSuspenduException(idCompte);
        }
        if (compte.getSolde().compareTo(BigDecimal.ZERO) != 0) {
            throw new CompteNonVideException(idCompte);
        }

        compteRepository.deleteById(idCompte);
    }

    /**
     * Recharge (crédite) un compte d'un montant donné.
     * Correspond à l'endpoint POST /v1/accounts/{idC}/recharge.
     *
     * La mise à jour du solde ne se fait jamais par écriture directe (RG15) : une
     * ligne de mouvement CREDIT est d'abord enregistrée dans le journal comptable
     * via GestionnaireJournal, puis le solde est recalculé à partir du journal.
     *
     * @throws CompteIntrouvableException si idCompte ne correspond à aucun compte
     * @throws CompteSuspenduException    si le compte est suspendu (RG17)
     * @throws ValidationException        si montant est nul ou négatif ou nul
     */
    public Compte recharger(Integer idCompte, BigDecimal montant) {
        Compte compte = lireCompte(idCompte);

        if (compte.getStatut() == StatutCompte.SUSPENDU) {
            throw new CompteSuspenduException(idCompte);
        }
        validerMontantPositif(montant);

        journalComptableService.enregistrerCredit(idCompte, montant, "Recharge du compte " + compte.getNumero());

        BigDecimal nouveauSolde = journalComptableService.calculerSoldeDepuisJournal(idCompte);
        compte.setSolde(nouveauSolde);
        compte.setDateMaj(LocalDateTime.now());

        return compteRepository.save(compte);
    }

    /**
     * Retire (débite) un montant d'un compte.
     * Correspond à l'endpoint POST /v1/accounts/{idC}/retrait.
     *
     * Même principe que {@link #recharger} : le solde n'est jamais décrémenté
     * directement, il découle de l'écriture d'une ligne DEBIT dans le journal.
     *
     * @throws CompteIntrouvableException si idCompte ne correspond à aucun compte
     * @throws CompteSuspenduException    si le compte est suspendu (RG17)
     * @throws ValidationException        si montant est nul ou négatif
     * @throws SoldeInsuffisantException  si montant est supérieur au solde disponible
     */
    public Compte retirer(Integer idCompte, BigDecimal montant) {
        Compte compte = lireCompte(idCompte);

        if (compte.getStatut() == StatutCompte.SUSPENDU) {
            throw new CompteSuspenduException(idCompte);
        }
        validerMontantPositif(montant);

        if (montant.compareTo(compte.getSolde()) > 0) {
            throw new SoldeInsuffisantException(idCompte, montant, compte.getSolde());
        }

        journalComptableService.enregistrerDebit(idCompte, montant, "Retrait du compte " + compte.getNumero());

        BigDecimal nouveauSolde = journalComptableService.calculerSoldeDepuisJournal(idCompte);
        compte.setSolde(nouveauSolde);
        compte.setDateMaj(LocalDateTime.now());

        return compteRepository.save(compte);
    }

    /**
     * Consulte le solde d'un compte, en vérifiant sa cohérence avec le journal
     * comptable (RG32). En cas d'écart, le compte est automatiquement suspendu
     * et une alerte de sécurité est déclenchée avant que l'exception ne soit levée.
     * Correspond à l'endpoint GET /v1/accounts/{idC}/solde.
     *
     * @throws CompteIntrouvableException  si idCompte ne correspond à aucun compte
     * @throws IncoherenceSoldeException   si le solde stocké diverge du solde recalculé
     */
    public BigDecimal consulterSolde(Integer idCompte) {
        Compte compte = lireCompte(idCompte);

        BigDecimal soldeRecalcule = journalComptableService.calculerSoldeDepuisJournal(idCompte);

        if (soldeRecalcule.compareTo(compte.getSolde()) != 0) {
            compte.setStatut(StatutCompte.SUSPENDU);
            compte.setDateMaj(LocalDateTime.now());
            compteRepository.save(compte);

            BigDecimal ecart = soldeRecalcule.subtract(compte.getSolde());
            journalComptableService.declencherAlerteSecurite(idCompte, ecart);

            throw new IncoherenceSoldeException(idCompte, compte.getSolde(), soldeRecalcule);
        }

        return compte.getSolde();
    }

    // ------------------------------------------------------------------
    // Méthodes utilitaires privées
    // ------------------------------------------------------------------

    /** Génère un numéro de compte unique (RG13). Implémentation à adapter selon le format retenu. */
    private String genererNumeroCompte() {
        return "CPT-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    /** RG14 : la devise doit appartenir à la liste des devises supportées par la plateforme. */
    private void validerDevise(String devise) {
        if (devise == null || devise.isBlank() || !DEVISES_SUPPORTEES.contains(devise.toUpperCase())) {
            throw new ValidationException("devise");
        }
    }

    /** Validation commune à recharger/retirer : le montant doit être strictement positif. */
    private void validerMontantPositif(BigDecimal montant) {
        if (montant == null || montant.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("montant");
        }
    }
}