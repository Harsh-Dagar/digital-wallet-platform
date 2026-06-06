package com.wallet.digital_wallet.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "transactions")
public class Transaction {

    @Id
    private String id;

    @Indexed
    private String walletId;

    @Indexed
    private String userId;

    private TransactionType type;
    private TransactionStatus status;

    private BigDecimal amount;
    private String currency;

    private String counterpartyWalletId;
    private String description;

    @Indexed(unique = true, sparse = true)
    private String idempotencyKey;

    @CreatedDate
    private Instant createdAt;

    public enum TransactionType {
        CREDIT, DEBIT
    }

    public enum TransactionStatus {
        PENDING, SUCCESS, FAILED
    }
}