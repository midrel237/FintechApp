package com.fintechApp.persistance.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "utilisateur")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_u")
    private Integer id;

    @Column(name = "nom_u", length = 100, nullable = false)
    private String nom;

    @Column(name = "prenom_u", length = 100, nullable = false)
    private String prenom;

    @Column(name = "email_u", length = 255, unique = true, nullable = false)
    private String email;

    @Column(name = "telephone_u", length = 20)
    private String telephone;

    @Column(name = "adresse_u", length = 255)
    private String adresse;

    @Column(name = "mot_passe", length = 255, nullable = false)
    private String motPasse;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_u", length = 20)
    private StatutUtilisateur statut;

    @Column(name = "code_validation", length = 100)
    private String codeValidation;

    @Column(name = "date_creation",  nullable = false)
    private LocalDateTime dateCreation;

    @Column(name = "expiration_code")
    private LocalDateTime expirationCode;

    @Column(name = "date_maj",  nullable = false)
    private LocalDateTime dateMaj;

    public boolean estActif() {
        return statut == StatutUtilisateur.actif;
    }

}
