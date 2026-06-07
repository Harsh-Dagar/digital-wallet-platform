package com.wallet.digital_wallet.controller;

import com.wallet.digital_wallet.dto.request.DepositRequest;
import com.wallet.digital_wallet.dto.request.TransferRequest;
import com.wallet.digital_wallet.dto.response.TransactionResponse;
import com.wallet.digital_wallet.dto.response.WalletResponse;
import com.wallet.digital_wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    private String getCurrentUserId() {
        return SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
    }

    @PostMapping
    public ResponseEntity<WalletResponse> createWallet() {
        String userId = getCurrentUserId();
        WalletResponse response = walletService.createWallet(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<WalletResponse> getWallet() {
        String userId = getCurrentUserId();
        WalletResponse response = walletService.getWallet(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(@Valid @RequestBody DepositRequest request) {
        String userId = getCurrentUserId();
        TransactionResponse response = walletService.deposit(userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransferRequest request) {
        String userId = getCurrentUserId();
        TransactionResponse response = walletService.transfer(userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionResponse>> getTransactions() {
        String userId = getCurrentUserId();
        List<TransactionResponse> response = walletService.getTransactionHistory(userId);
        return ResponseEntity.ok(response);
    }
}
