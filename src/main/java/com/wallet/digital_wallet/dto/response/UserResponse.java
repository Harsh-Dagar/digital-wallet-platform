package com.wallet.digital_wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class UserResponse {
    private String id;
    private String name;
    private String email;
    private Instant createdAt;
}
