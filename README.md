# SecurePay – Merchant Transaction API

A production-grade merchant transaction processing engine built with **Java 21**, **Spring Boot 3**, **PostgreSQL**, **REST APIs**, and **JWT**.

---

## Architecture Highlights

1. **Merchant Registration & Key Management**:
   - Secure merchant registration with bcrypt credential encryption.
   - Dual-tier authentication:
     - **JWT Bearer Authentication** for administrative portal access (`ROLE_MERCHANT_ADMIN`, `ROLE_MERCHANT_OPERATOR`).
     - **API Key Authentication** (`X-API-KEY: sec_live_...`) for automated server-to-server transaction requests.
   - API keys are hashed using SHA-256 before storage; the raw secret is only displayed once upon generation.

2. **Full Payment Lifecycle**:
   - Double-entry state machine enforcing valid transitions:
     $$\text{CREATED} \longrightarrow \text{AUTHORIZED} \longrightarrow \text{CAPTURED} \longrightarrow \text{REFUNDED}$$
   - Partial captures and partial refunds tracking with remaining balance checks.

3. **Payload-Based Idempotency**:
   - Clients supply an `Idempotency-Key` header with mutating requests.
   - Server computes a SHA-256 digest of the request payload and checks against previous submissions:
     - **Same Key + Same Payload**: Returns cached response immediately with `Idempotent-Replay: true` without re-executing transactions.
     - **Same Key + Different Payload**: Rejects request with `409 Conflict` (*"Conflicting replay detected"*).
     - **Concurrent In-Progress Request**: Throws conflict to prevent race conditions.

4. **Out-of-Order Webhook Event Handling**:
   - Network delays or third-party gateways can emit webhooks out-of-order (e.g. `PAYMENT_CAPTURED` before `PAYMENT_AUTHORIZED`).
   - The transaction state machine inspects sequence numbers and current status:
     - Non-regressive: Does not roll back a `CAPTURED` or `REFUNDED` payment when a delayed `PAYMENT_AUTHORIZED` event arrives.
     - Identifies out-of-order events as `IGNORED_OUT_OF_ORDER` and preserves transaction integrity.

5. **Security, Rate Limiting & Audit Logging**:
   - Token-bucket algorithm (Bucket4j) per API key / IP address.
   - Jakarta Bean Validation on all incoming DTOs.
   - Comprehensive audit logging recording actor, action, resource, IP, status, and execution duration.

---

## API Endpoints

### 1. Authentication & Onboarding
- `POST /api/v1/auth/register` - Register a new merchant and admin account
- `POST /api/v1/auth/login` - Authenticate admin/operator and acquire JWT token

### 2. API Key Management (Requires `ROLE_MERCHANT_ADMIN`)
- `POST /api/v1/api-keys` - Generate a new scoped API key (`sec_live_...`)
- `GET /api/v1/api-keys` - List merchant API keys
- `DELETE /api/v1/api-keys/{id}` - Revoke an API key

### 3. Payment Processing (Requires `X-API-KEY` or Bearer JWT)
- `POST /api/v1/payments/authorize` - Reserve funds (`Idempotency-Key` supported)
- `POST /api/v1/payments/{paymentId}/capture` - Settle funds (`Idempotency-Key` supported)
- `POST /api/v1/payments/{paymentId}/refund` - Reverse captured funds (`Idempotency-Key` supported)
- `GET /api/v1/payments/{paymentId}` - Query transaction details
- `GET /api/v1/payments` - List merchant transactions

### 4. Webhooks & Audit
- `POST /api/v1/webhooks/events` - Process external payment provider webhooks
- `GET /api/v1/audit-logs` - Inspect historical merchant audit logs

---

## Getting Started

### Prerequisites
- Java 21+
- Apache Maven 3.9+
- Docker & Docker Compose (optional for local PostgreSQL)

### 1. Start PostgreSQL with Docker
```bash
docker compose up -d
```

### 2. Run the Application
```bash
mvn spring-boot:run
```

### 3. Run Tests
```bash
mvn clean test
```
