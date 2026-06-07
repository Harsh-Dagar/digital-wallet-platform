package com.wallet.digital_wallet.dto.response;

import com.wallet.digital_wallet.model.Transaction.TransactionType;
import com.wallet.digital_wallet.model.Transaction.TransactionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class TransactionResponse {
    private String id;
    private String walletId;
    private String userId;
    private TransactionType type;
    private TransactionStatus status;
    private BigDecimal amount;
    private String currency;
    private String counterpartyWalletId;
    private String description;
    private Instant createdAt;
}
