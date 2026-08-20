-- ============================================================================
-- Script de création de la base de données
-- Projet : Plateforme de paiement numérique
-- Entités : Utilisateur, Compte, Transaction, Journal_comptable
-- Dialecte cible : PostgreSQL (types SERIAL, CHECK, TRIGGER)
-- ============================================================================

-- Nettoyage préalable, dans l'ordre inverse des dépendances
DROP TABLE IF EXISTS journal_comptable CASCADE;
DROP TABLE IF EXISTS transaction CASCADE;
DROP TABLE IF EXISTS compte CASCADE;
DROP TABLE IF EXISTS utilisateur CASCADE;


-- ============================================================================
-- Table : utilisateur
-- ============================================================================
CREATE TABLE utilisateur (
    id_u            SERIAL        PRIMARY KEY,
    nom_u           VARCHAR(100)  NOT NULL,
    prenom_u        VARCHAR(100)  NOT NULL,
    email_u         VARCHAR(255)  NOT NULL,
    telephone_u     VARCHAR(20),
    adresse_u       VARCHAR(255),
    mot_passe       VARCHAR(255)  NOT NULL,              -- mot de passe haché, jamais stocké en clair (RG)
    statut_u        VARCHAR(20)   NOT NULL DEFAULT 'VERROUILLE',
    code_validation  VARCHAR(100),                             -- code de validation envoyé par email pour passer à l'état "actif"
    date_creation   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expiration_code   TIMESTAMP,                                 -- date d'expiration du code de validation
    date_maj        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_utilisateur_email   UNIQUE (email_u),
    -- Valeurs alignées sur les noms des constantes de l'enum Java StatutUtilisateur
    -- (Hibernate persiste le nom exact de la constante avec @Enumerated(EnumType.STRING),
    -- donc en MAJUSCULES : 'VERROUILLE' / 'ACTIF', jamais en minuscules).
    CONSTRAINT ck_utilisateur_statut  CHECK (statut_u IN ('verrouille', 'actif'))
);

COMMENT ON TABLE  utilisateur IS 'Un utilisateur est créé avec le statut "verrouille" ; il ne peut interagir avec le système qu''une fois passé à "ACTIF" (RG)';
COMMENT ON COLUMN utilisateur.statut_u IS 'verrouille = en attente de validation ; actif = autorisé à créer des comptes et initier des transactions';


-- ============================================================================
-- Table : compte
-- ============================================================================
CREATE TABLE compte (
    id_c            SERIAL        PRIMARY KEY,
    numero_c        VARCHAR(34)   NOT NULL,
    id_u            INTEGER       NOT NULL REFERENCES utilisateur(id_u),
    type_c          VARCHAR(20)   NOT NULL,
    devise_c        VARCHAR(3)    NOT NULL,               -- code ISO 4217 (EUR, USD, XAF...)
    solde_c         DECIMAL(15,2) NOT NULL DEFAULT 0,      -- champ calculé (RG) : ne jamais écrire directement depuis l'API
    statut_c        VARCHAR(20)   NOT NULL DEFAULT 'ACTIF',
    date_creation_c TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_maj_c      TIMESTAMP,

    CONSTRAINT uq_compte_numero          UNIQUE (numero_c),
    CONSTRAINT ck_compte_type            CHECK (type_c IN ('EPARGNE', 'COURANT')),
    -- Alignées sur l'enum Java StatutCompte { ACTIF, SUSPENDU } (EnumType.STRING -> MAJUSCULES)
    CONSTRAINT ck_compte_statut          CHECK (statut_c IN ('ACTIF', 'SUSPENDU')),
    CONSTRAINT ck_compte_solde_positif   CHECK (solde_c >= 0)
);

COMMENT ON TABLE  compte IS 'Un compte est rattaché à un seul utilisateur actif ; un compte "suspendu" bloque toute opération (RG)';
COMMENT ON COLUMN compte.solde_c IS 'Doit toujours correspondre à la somme des mouvements du journal comptable pour ce compte (RG) ; tout écart doit entraîner la suspension du compte, gérée au niveau applicatif';

CREATE INDEX idx_compte_id_u   ON compte(id_u);
CREATE INDEX idx_compte_statut ON compte(statut_c);


-- ============================================================================
-- Table : transaction
-- ============================================================================
CREATE TABLE transaction (
    id_t                    SERIAL        PRIMARY KEY,
    ref_t                   VARCHAR(50)   NOT NULL,
    id_compte_source        INTEGER       NOT NULL REFERENCES compte(id_c),
    id_compte_destination   INTEGER       NOT NULL REFERENCES compte(id_c),
    montant_t               DECIMAL(15,2) NOT NULL,
    description_t           VARCHAR(255),
    statut_t                VARCHAR(20)   NOT NULL DEFAULT 'en_attente',
    date_creation_t         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_validation_t       TIMESTAMP,
    date_suspension_t        TIMESTAMP,
    date_annulation_t        TIMESTAMP,

    CONSTRAINT uq_transaction_reference           UNIQUE (ref_t),
    CONSTRAINT ck_transaction_statut              CHECK (statut_t IN ('en_attente', 'validee', 'echouee', 'annulee', 'suspendue')),
    CONSTRAINT ck_transaction_montant_positif     CHECK (montant_t > 0),
    CONSTRAINT ck_transaction_comptes_distincts   CHECK (id_compte_source <> id_compte_destination)
);

COMMENT ON TABLE transaction IS 'Virement en deux temps : création à l''état "en_attente" (aucun fonds déplacé), puis confirmation qui fait passer le statut à "validee" et génère l''écriture du journal comptable. Une transaction "validee", "echouee" ou "annulee" est immuable (RG)';

CREATE INDEX idx_transaction_source      ON transaction(id_compte_source);
CREATE INDEX idx_transaction_destination ON transaction(id_compte_destination);
CREATE INDEX idx_transaction_statut      ON transaction(statut_t);


-- ============================================================================
-- Table : journal_comptable
-- ============================================================================
CREATE TABLE journal_comptable (
    id_j                    SERIAL        PRIMARY KEY,
    date_enreg_j            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    libelle_j               VARCHAR(255)  NOT NULL,
    num_compte_debit        VARCHAR(34)   NOT NULL REFERENCES compte(numero_c),
    num_compte_credit       VARCHAR(34)   NOT NULL REFERENCES compte(numero_c),
    montant_debit           DECIMAL(15,2) NOT NULL ,
    montant_credit          DECIMAL(15,2) NOT NULL ,
    id_transaction          INTEGER       REFERENCES transaction(id_t),        -- NULL pour une recharge / un retrait hors virement
    id_ligne_origine        INTEGER       REFERENCES journal_comptable(id_j),  -- renseigné uniquement pour une ligne de contre-passation
    motif                   VARCHAR(255)                                    -- obligatoire uniquement pour une contre-passation
);

COMMENT ON TABLE journal_comptable IS 'Aucune ligne ne peut être modifiée ou supprimée (RG) ; toute correction se fait exclusivement par une nouvelle ligne référençant id_ligne_origine (contre-passation), voir triggers ci-dessous';

CREATE INDEX idx_journal_compte      ON journal_comptable(num_compte_debit);
CREATE INDEX idx_journal_transaction ON journal_comptable(id_transaction);


-- ============================================================================
-- Application de la règle d'immuabilité du journal comptable :
-- toute tentative de UPDATE ou DELETE sur une ligne existante est bloquée
-- ============================================================================
CREATE OR REPLACE FUNCTION fn_journal_comptable_immuable()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Le journal comptable est immuable : une ligne ne peut être ni modifiée ni supprimée (RG Partie 0). Utilisez une ligne de contre-passation (id_ligne_origine).';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_journal_comptable_no_update
    BEFORE UPDATE ON journal_comptable
    FOR EACH ROW EXECUTE FUNCTION fn_journal_comptable_immuable();

CREATE TRIGGER trg_journal_comptable_no_delete
    BEFORE DELETE ON journal_comptable
    FOR EACH ROW EXECUTE FUNCTION fn_journal_comptable_immuable();
