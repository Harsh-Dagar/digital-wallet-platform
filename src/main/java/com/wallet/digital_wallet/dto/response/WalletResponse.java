package com.wallet.digital_wallet.dto.response;

import com.wallet.digital_wallet.model.Wallet.WalletStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class WalletResponse {
    private String id;
    private String userId;
    private BigDecimal balance;
    private String currency;
    private WalletStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
