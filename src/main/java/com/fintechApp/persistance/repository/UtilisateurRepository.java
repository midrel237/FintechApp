package com.fintechApp.persistance.repository;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import com.fintechApp.persistance.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Integer> {
    // findByEmail (=) était sensible à la casse sur PostgreSQL : "Jean@Mail.com"
    // et "jean@mail.com" étaient traités comme deux utilisateurs distincts,
    // cassant à la fois l'unicité (RG A) et la connexion. IgnoreCase corrige
    // ce comportement à la source, pour tous les appelants.
    Optional<Utilisateur> findByEmailIgnoreCase(String email);
}