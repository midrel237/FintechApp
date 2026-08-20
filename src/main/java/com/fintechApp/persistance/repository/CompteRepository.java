package com.fintechApp.persistance.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fintechApp.persistance.entity.Compte;

import jakarta.persistence.LockModeType;

@Repository
public interface CompteRepository extends JpaRepository<Compte, Integer> {

    Optional<Compte> findByNumero(String numero);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Compte c WHERE c.numero = :numero")
    Optional<Compte> findByNumeroForUpdate(@Param("numero") String numero);

    // idUtilisateur est une relation (Compte -> Utilisateur), pas un Integer :
    // la dérivation automatique Spring Data ("findByIdUtilisateur(Integer)")
    // ne peut pas comparer directement un id scalaire à une entité associée.
    // On navigue explicitement jusqu'à l'id de l'utilisateur en JPQL.
    @Query("SELECT c FROM Compte c WHERE c.idUtilisateur.id = :idUtilisateur")
    List<Compte> findByIdUtilisateur(@Param("idUtilisateur") Integer idUtilisateur);
}
