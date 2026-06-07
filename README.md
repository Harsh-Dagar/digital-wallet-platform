# Digital Wallet Platform

A backend system for moving money between wallets. Built with correctness and performance in mind — idempotent transfers, cached reads, and async event processing.

## Stack

- Java 17 + Spring Boot 3.5
- MongoDB
- Redis
- Kafka
- Docker Compose
- k6 for load testing

## How it works

One backend service. MongoDB is the source of truth. Redis handles caching and rate limiting. Kafka handles async events like notifications and fraud checks.

## Features

- JWT based auth
- One wallet per user
- Deposit and transfer money
- Idempotency on transfers — retrying the same request never moves money twice
- Transaction state machine — PENDING → SUCCESS / FAILED
- Rate limiting on transfer endpoint
- Wallet balance cached in Redis
- Kafka consumers for notifications, fraud detection, and analytics

## Setup

### Prerequisites
- Java 17
- Maven
- Docker

### Run locally

```bash
git clone https://github.com/Harsh-Dagar/digital-wallet-platform.git
cd digital-wallet-platform

cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# fill in your JWT secret

docker-compose -f docker/docker-compose.yml up -d

export SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run
```

App runs on `http://localhost:8080`

## API

### Auth
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/auth/register` | Register | No |
| POST | `/api/auth/login` | Login | No |

### Wallet
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/wallets` | Create wallet | Yes |
| GET | `/api/wallets/me` | Get wallet | Yes |
| POST | `/api/wallets/deposit` | Deposit | Yes |
| POST | `/api/wallets/transfer` | Transfer | Yes |
| GET | `/api/wallets/transactions` | History | Yes |

## Sample requests

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name": "Harsh", "email": "harsh@example.com", "password": "password123"}'

# Transfer
curl -X POST http://localhost:8080/api/wallets/transfer \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"toWalletId": "<walletId>", "amount": 500, "idempotencyKey": "txn-001"}'
```

## Design choices

- BigDecimal for all money — never double or float
- Idempotency key stored with transaction — duplicate check happens before any money moves
- Transfer creates two records — DEBIT for sender, CREDIT for receiver
- Indexes on userId, walletId, transactionId for fast reads
- Secrets never committed — use application-local.yml locally

## Limitations

- Transfers are not wrapped in MongoDB transactions yet — requires replica set
- Fraud detection is rule based, not ML
