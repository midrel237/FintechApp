package com.fintechApp.persistance.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "journal_comptable")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JournalComptable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_j")
    private Integer id;

    @Column(name = "date_enreg_j", nullable = false)
    private LocalDateTime dateEnregistrement;

    @Column(name = "libelle_j", length = 255, nullable = false)
    private String libelle;

    @ManyToOne
    @JoinColumn(name = "num_compte_debit", referencedColumnName = "numero_c", nullable = false)
    private Compte compteDebit;

    @ManyToOne
    @JoinColumn(name = "num_compte_credit", referencedColumnName = "numero_c", nullable = false)
    private Compte compteCredit;

    @Column(name = "montant_debit", precision = 15, scale = 2, nullable = false)
    private BigDecimal montantDebit;

    @Column(name = "montant_credit", precision = 15, scale = 2, nullable = false)
    private BigDecimal montantCredit;

    @ManyToOne
    @JoinColumn(name = "id_transaction")
    private Transaction transaction;

    @ManyToOne
    @JoinColumn(name = "id_ligne_origine")
    private JournalComptable ligneOrigine;

    @Column(name = "motif", length = 255)
    private String motif;

    // Les anciennes méthodes enregistrerMouvement / calculerSoldeDepuisJournal /
    // declencherAlerteSecurite (stubs "throw new UnsupportedOperationException")
    // ont été retirées d'ici : une @Entity ne doit pas dépendre d'un repository.
    // Cette logique vit désormais dans JournalComptableService (couche Métier).
}
