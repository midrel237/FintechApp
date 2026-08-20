package com.fintechApp.persistance.repository;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import com.fintechApp.persistance.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Integer> {
    Optional<Utilisateur> findByEmail(String email);
}