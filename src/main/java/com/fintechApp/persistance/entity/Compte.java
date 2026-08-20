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
@Table(name = "compte")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Compte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_c")
    private Integer idCompte;

    @Column(name = "numero_c", length = 34, unique = true, nullable = false)
    private String numero;

    @ManyToOne
    @JoinColumn(name = "id_u", nullable = false)
    private Utilisateur idUtilisateur;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_c", length = 20, nullable = false)
    private TypeCompte type;

    @Column(name = "devise_c", length = 3, nullable = false)
    private String devise;

    @Column(name = "solde_c", precision = 15, scale = 2, nullable = false)
    private BigDecimal solde;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_c")
    private StatutCompte statut;

    @Column(name = "date_creation_c", nullable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_maj_c", nullable = false)
    private LocalDateTime dateMaj;

}