package com.wallet.digital_wallet.repository;

import com.wallet.digital_wallet.model.Transaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends MongoRepository<Transaction, String> {
    List<Transaction> findByWalletIdOrderByCreatedAtDesc(String walletId);
    List<Transaction> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}