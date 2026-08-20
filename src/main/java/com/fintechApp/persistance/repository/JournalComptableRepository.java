package com.fintechApp.persistance.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fintechApp.persistance.entity.Compte;
import com.fintechApp.persistance.entity.JournalComptable;

@Repository
public interface JournalComptableRepository extends JpaRepository<JournalComptable, Integer>,
        JpaSpecificationExecutor<JournalComptable> {

    List<JournalComptable> findByCompteDebitOrCompteCredit(Compte debit, Compte credit);

    // Support du recalcul de solde (RG32) : somme des mouvements crédit/débit
    // du journal comptable pour un compte donné. COALESCE(..., 0) évite un
    // NULL quand le compte n'a encore aucune ligne de journal.
    @Query("SELECT COALESCE(SUM(j.montantCredit), 0) FROM JournalComptable j WHERE j.compteCredit = :compte")
    BigDecimal sommeCredits(@Param("compte") Compte compte);

    @Query("SELECT COALESCE(SUM(j.montantDebit), 0) FROM JournalComptable j WHERE j.compteDebit = :compte")
    BigDecimal sommeDebits(@Param("compte") Compte compte);

    // 4.a) GET /api/v1/journalComptable — liste paginée avec filtres optionnels
    // (dateDebut, dateFin, idCompte). JpaSpecificationExecutor (voir
    // JournalComptableService#construireSpecification) plutôt qu'un JPQL du
    // type "(:param IS NULL OR ...)" : sous PostgreSQL, un paramètre lié qui
    // n'apparaît QUE dans un "? IS NULL" (sans être aussi comparé à une
    // colonne typée) fait échouer le driver avec "could not determine data
    // type of parameter $1" (SQLState 42P18), faute de contexte de type. La
    // Specification évite le problème à la racine : un filtre absent
    // n'ajoute tout simplement aucun prédicat, donc aucun paramètre "orphelin".
}

