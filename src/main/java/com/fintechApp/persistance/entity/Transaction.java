package com.fintechApp.persistance.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "transaction")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_t")
    private Integer id;

    @Column(name = "ref_t", length = 50, unique = true, nullable = false)
    private String reference;

    @ManyToOne
    @JoinColumn(name = "id_compte_source", nullable = false)
    private Compte compteSource;

    @ManyToOne
    @JoinColumn(name = "id_compte_destination", nullable = false)
    private Compte compteDestination;

    @Column(name = "montant_t", precision = 15, scale = 2, nullable = false)
    private BigDecimal montant;

    // Nullable en base (pas de NOT NULL sur description_t dans le schéma).
    @Column(name = "description_t", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_t", length = 20, nullable = false)
    private StatutTransaction statut;

    @Column(name = "date_creation_t", nullable = false)
    private LocalDateTime dateCreation;

    // Renseignée uniquement à la confirmation -> doit rester nullable.
    @Column(name = "date_validation_t")
    private LocalDateTime dateValidation;

    // Renseignée uniquement à la suspension -> doit rester nullable.
    @Column(name = "date_suspension_t")
    private LocalDateTime dateSuspension;

    // Renseignée uniquement à l'annulation -> doit rester nullable.
    @Column(name = "date_annulation_t")
    private LocalDateTime dateAnnulation;
}
