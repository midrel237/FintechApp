package com.fintechApp.persistance.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fintechApp.persistance.entity.Transaction;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {
    @SuppressWarnings("unchecked")
    @Override
    Transaction save(Transaction transaction);
    @Override
    Optional<Transaction> findById(Integer id);
    boolean existsByReference(String reference);
    Optional<Transaction> findByReference(String reference);
}
