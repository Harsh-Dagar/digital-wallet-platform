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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final RedisService redisService;

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
        WalletResponse response = mapToWalletResponse(saved);

        redisService.cacheWallet(userId, response);
        return response;
    }

    public WalletResponse getWallet(String userId) {
        WalletResponse cached = redisService.getCachedWallet(userId);
        if (cached != null) {
            log.debug("Wallet cache hit for user: {}", userId);
            return cached;
        }

        log.debug("Wallet cache miss for user: {}", userId);
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        WalletResponse response = mapToWalletResponse(wallet);
        redisService.cacheWallet(userId, response);
        return response;
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

        // evict cache so next read gets fresh balance
        redisService.evictWalletCache(userId);
        redisService.evictRecentTransactionsCache(userId);

        return mapToTransactionResponse(saved);
    }

    // TODO: Wrap transfer operations in MongoDB transaction (@Transactional)
    // Requires MongoDB replica set in docker-compose
    // Currently not atomic — if crash between steps, data can be inconsistent
    public TransactionResponse transfer(String userId, TransferRequest request) {
        // rate limit check
        if (redisService.isRateLimited(userId)) {
            throw new RuntimeException("Rate limit exceeded. Max 5 transfers per minute.");
        }

        // idempotency check in Redis
        if (redisService.isIdempotencyKeyPresent(request.getIdempotencyKey())) {
            throw new RuntimeException("Duplicate request detected");
        }

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

        // store idempotency key in Redis before processing
        redisService.storeIdempotencyKey(request.getIdempotencyKey());

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

        // evict caches for both users
        redisService.evictWalletCache(userId);
        redisService.evictWalletCache(receiverWallet.getUserId());
        redisService.evictRecentTransactionsCache(userId);
        redisService.evictRecentTransactionsCache(receiverWallet.getUserId());

        return mapToTransactionResponse(saved);
    }

    public List<TransactionResponse> getTransactionHistory(String userId) {
        List<TransactionResponse> cached = redisService.getCachedRecentTransactions(userId);
        if (cached != null) {
            log.debug("Transaction cache hit for user: {}", userId);
            return cached;
        }

        log.debug("Transaction cache miss for user: {}", userId);
        List<TransactionResponse> transactions = transactionRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToTransactionResponse)
                .collect(Collectors.toList());

        redisService.cacheRecentTransactions(userId, transactions);
        return transactions;
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
