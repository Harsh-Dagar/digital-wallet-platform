package com.wallet.digital_wallet.service;

import com.wallet.digital_wallet.dto.request.DepositRequest;
import com.wallet.digital_wallet.dto.request.TransferRequest;
import com.wallet.digital_wallet.dto.response.TransactionResponse;
import com.wallet.digital_wallet.dto.response.WalletResponse;
import com.wallet.digital_wallet.model.Transaction;
import com.wallet.digital_wallet.model.Transaction.TransactionStatus;
import com.wallet.digital_wallet.model.Transaction.TransactionType;
import com.wallet.digital_wallet.model.Wallet;
import com.wallet.digital_wallet.model.Wallet.WalletStatus;
import com.wallet.digital_wallet.repository.TransactionRepository;
import com.wallet.digital_wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public WalletResponse createWallet(String userId) {
        if (walletRepository.existsByUserId(userId)) {
            throw new RuntimeException("Wallet already exists for this user");
        }

        Wallet wallet = Wallet.builder()
                .userId(userId)
                .balance(BigDecimal.ZERO)
                .currency("INR")
                .status(WalletStatus.ACTIVE)
                .build();

        Wallet saved = walletRepository.save(wallet);
        return mapToWalletResponse(saved);
    }

    public WalletResponse getWallet(String userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));
        return mapToWalletResponse(wallet);
    }

    public TransactionResponse deposit(String userId, DepositRequest request) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new RuntimeException("Wallet is not active");
        }

        // create transaction in PENDING state
        Transaction transaction = Transaction.builder()
                .walletId(wallet.getId())
                .userId(userId)
                .type(TransactionType.CREDIT)
                .status(TransactionStatus.PENDING)
                .amount(request.getAmount())
                .currency(wallet.getCurrency())
                .description("Deposit")
                .build();

        transactionRepository.save(transaction);

        // update balance
        wallet.setBalance(wallet.getBalance().add(request.getAmount()));
        walletRepository.save(wallet);

        // mark transaction as SUCCESS
        transaction.setStatus(TransactionStatus.SUCCESS);
        Transaction saved = transactionRepository.save(transaction);

        return mapToTransactionResponse(saved);
    }

    public TransactionResponse transfer(String userId, TransferRequest request) {
        // idempotency check
        transactionRepository.findByIdempotencyKey(request.getIdempotencyKey())
                .ifPresent(t -> {
                    throw new RuntimeException("Duplicate request detected");
                });

        Wallet senderWallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Sender wallet not found"));

        Wallet receiverWallet = walletRepository.findById(request.getToWalletId())
                .orElseThrow(() -> new RuntimeException("Receiver wallet not found"));

        if (senderWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new RuntimeException("Sender wallet is not active");
        }

        if (receiverWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new RuntimeException("Receiver wallet is not active");
        }

        if (senderWallet.getId().equals(receiverWallet.getId())) {
            throw new RuntimeException("Cannot transfer to same wallet");
        }

        if (senderWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("Insufficient balance");
        }

        // create debit transaction in PENDING state
        Transaction debitTxn = Transaction.builder()
                .walletId(senderWallet.getId())
                .userId(userId)
                .type(TransactionType.DEBIT)
                .status(TransactionStatus.PENDING)
                .amount(request.getAmount())
                .currency(senderWallet.getCurrency())
                .counterpartyWalletId(receiverWallet.getId())
                .idempotencyKey(request.getIdempotencyKey())
                .description("Transfer sent")
                .build();

        transactionRepository.save(debitTxn);

        // deduct from sender
        senderWallet.setBalance(senderWallet.getBalance().subtract(request.getAmount()));
        walletRepository.save(senderWallet);

        // add to receiver
        receiverWallet.setBalance(receiverWallet.getBalance().add(request.getAmount()));
        walletRepository.save(receiverWallet);

        // create credit transaction for receiver
        Transaction creditTxn = Transaction.builder()
                .walletId(receiverWallet.getId())
                .userId(receiverWallet.getUserId())
                .type(TransactionType.CREDIT)
                .status(TransactionStatus.SUCCESS)
                .amount(request.getAmount())
                .currency(receiverWallet.getCurrency())
                .counterpartyWalletId(senderWallet.getId())
                .description("Transfer received")
                .build();

        transactionRepository.save(creditTxn);

        // mark debit transaction as SUCCESS
        debitTxn.setStatus(TransactionStatus.SUCCESS);
        Transaction saved = transactionRepository.save(debitTxn);

        return mapToTransactionResponse(saved);
    }

    public List<TransactionResponse> getTransactionHistory(String userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToTransactionResponse)
                .collect(Collectors.toList());
    }

    private WalletResponse mapToWalletResponse(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUserId())
                .balance(wallet.getBalance())
                .currency(wallet.getCurrency())
                .status(wallet.getStatus())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

    private TransactionResponse mapToTransactionResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .walletId(transaction.getWalletId())
                .userId(transaction.getUserId())
                .type(transaction.getType())
                .status(transaction.getStatus())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .counterpartyWalletId(transaction.getCounterpartyWalletId())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
