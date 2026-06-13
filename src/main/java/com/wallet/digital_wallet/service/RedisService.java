package com.wallet.digital_wallet.service;

import com.wallet.digital_wallet.dto.response.TransactionResponse;
import com.wallet.digital_wallet.dto.response.WalletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String WALLET_KEY_PREFIX = "wallet:";
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";
    private static final String RECENT_TRANSACTIONS_KEY_PREFIX = "recent_txns:";

    private static final long WALLET_TTL_MINUTES = 10;
    private static final long IDEMPOTENCY_TTL_HOURS = 24;
    private static final long RATE_LIMIT_TTL_SECONDS = 60;
    private static final long RECENT_TRANSACTIONS_TTL_MINUTES = 5;
    private static final int MAX_TRANSFERS_PER_MINUTE = 5;

    // Wallet cache
    public void cacheWallet(String userId, WalletResponse wallet) {
        String key = WALLET_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, wallet, WALLET_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("Cached wallet for user: {}", userId);
    }

    public WalletResponse getCachedWallet(String userId) {
        String key = WALLET_KEY_PREFIX + userId;
        return (WalletResponse) redisTemplate.opsForValue().get(key);
    }

    public void evictWalletCache(String userId) {
        String key = WALLET_KEY_PREFIX + userId;
        redisTemplate.delete(key);
        log.debug("Evicted wallet cache for user: {}", userId);
    }

    // Idempotency
    public boolean isIdempotencyKeyPresent(String idempotencyKey) {
        String key = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void storeIdempotencyKey(String idempotencyKey) {
        String key = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        redisTemplate.opsForValue().set(key, "1", IDEMPOTENCY_TTL_HOURS, TimeUnit.HOURS);
    }

    // Rate limiting
    public boolean isRateLimited(String userId) {
        String key = RATE_LIMIT_KEY_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, RATE_LIMIT_TTL_SECONDS, TimeUnit.SECONDS);
        }
        return count > MAX_TRANSFERS_PER_MINUTE;
    }

    // Recent transactions cache
    public void cacheRecentTransactions(String userId, List<TransactionResponse> transactions) {
        String key = RECENT_TRANSACTIONS_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, transactions, RECENT_TRANSACTIONS_TTL_MINUTES, TimeUnit.MINUTES);
    }

    @SuppressWarnings("unchecked")
    public List<TransactionResponse> getCachedRecentTransactions(String userId) {
        String key = RECENT_TRANSACTIONS_KEY_PREFIX + userId;
        return (List<TransactionResponse>) redisTemplate.opsForValue().get(key);
    }

    public void evictRecentTransactionsCache(String userId) {
        String key = RECENT_TRANSACTIONS_KEY_PREFIX + userId;
        redisTemplate.delete(key);
    }
}
