import sys
import time
import json
import urllib.request
import urllib.error
import threading
from concurrent.futures import ThreadPoolExecutor

BASE_URL = "http://localhost:8085"

def log(msg):
    print(f"[TEST] {msg}")

def request(method, path, body=None, headers=None):
    url = f"{BASE_URL}{path}"
    req_headers = {"Content-Type": "application/json"}
    if headers:
        req_headers.update(headers)
    
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        
    req = urllib.request.Request(url, data=data, headers=req_headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            resp_body = resp.read().decode("utf-8")
            resp_headers = dict(resp.headers)
            json_data = None
            if resp_body:
                try:
                    json_data = json.loads(resp_body)
                except Exception:
                    json_data = resp_body
            return resp.status, json_data, resp_headers
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        json_err = None
        if err_body:
            try:
                json_err = json.loads(err_body)
            except Exception:
                json_err = err_body
        return e.code, json_err, dict(e.headers)
    except Exception as e:
        return 0, str(e), {}

def run_suite():
    log("=== 1. Starting Health & Actuator Check ===")
    status, data, _ = request("GET", "/actuator/health")
    log(f"Actuator Health status: {status}, response: {data}")
    assert status == 200, f"Expected 200 but got {status}"

    unique = int(time.time())
    username = f"admin_{unique}"
    email = f"admin_{unique}@securepay.test"
    password = "StrongPassword@2026"
    business = f"TechCorp {unique}"

    log("\n=== 2. Merchant Registration ===")
    reg_body = {
        "businessName": business,
        "email": email,
        "username": username,
        "password": password
    }
    status, data, _ = request("POST", "/api/v1/auth/register", reg_body)
    log(f"Register status: {status}, merchantId: {data.get('id') if isinstance(data, dict) else data}")
    assert status == 201, f"Expected 201, got {status}: {data}"
    merchant_id = data["id"]

    log("\n=== 3. Merchant Login (JWT) ===")
    login_body = {"username": username, "password": password}
    status, data, _ = request("POST", "/api/v1/auth/login", login_body)
    log(f"Login status: {status}, token type: {data.get('tokenType') if isinstance(data, dict) else ''}")
    assert status == 200, f"Expected 200, got {status}"
    jwt_token = data["token"]
    jwt_headers = {"Authorization": f"Bearer {jwt_token}"}

    log("\n=== 4. API Key Generation ===")
    key_body = {"name": "Primary Production Key", "rateLimitPerMinute": 100}
    status, data, _ = request("POST", "/api/v1/api-keys", key_body, headers=jwt_headers)
    log(f"Generate API Key status: {status}, prefix: {data.get('keyPrefix')}")
    assert status == 201, f"Expected 201, got {status}"
    api_key_secret = data["secretKey"]
    log(f"Generated secret key: {api_key_secret[:12]}...")

    log("\n=== 5. List API Keys ===")
    status, data, _ = request("GET", "/api/v1/api-keys", headers=jwt_headers)
    log(f"List API Keys count: {len(data) if isinstance(data, list) else 0}")
    assert status == 200 and len(data) >= 1

    api_headers = {"X-API-KEY": api_key_secret}

    log("\n=== 6. Payment Authorization with Idempotency-Key ===")
    idem_key = f"idem-key-{unique}"
    auth_body = {
        "amount": 350.00,
        "currency": "USD",
        "customerEmail": "customer@example.com",
        "description": "Order #48291"
    }
    status, data, resp_hdrs = request("POST", "/api/v1/payments/authorize", auth_body, headers={**api_headers, "Idempotency-Key": idem_key})
    log(f"Authorize status: {status}, PaymentId: {data.get('id')}, Status: {data.get('status')}")
    assert status == 201, f"Expected 201, got {status}: {data}"
    payment_id = data["id"]

    log("\n=== 7. Idempotency Check: Exact Duplicate Replay ===")
    status, data2, resp_hdrs2 = request("POST", "/api/v1/payments/authorize", auth_body, headers={**api_headers, "Idempotency-Key": idem_key})
    is_replay = resp_hdrs2.get("Idempotent-Replay") or resp_hdrs2.get("idempotent-replay")
    log(f"Replay status: {status}, Idempotent-Replay header: {is_replay}, Same Payment ID: {data2.get('id') == payment_id}")
    assert status == 201, f"Expected 201 on replay, got {status}"
    assert is_replay == "true", f"Expected Idempotent-Replay: true header, got {is_replay}"
    assert data2["id"] == payment_id, "Replay returned different payment ID!"

    log("\n=== 8. Idempotency Check: Conflicting Replay (Modified Payload) ===")
    conflict_body = {
        "amount": 999.00,  # Modified amount with same key!
        "currency": "USD",
        "customerEmail": "hacker@example.com",
        "description": "Modified order"
    }
    status, data3, _ = request("POST", "/api/v1/payments/authorize", conflict_body, headers={**api_headers, "Idempotency-Key": idem_key})
    log(f"Conflicting Replay status: {status}, Error: {data3.get('error') if isinstance(data3, dict) else data3}")
    assert status == 409, f"Expected 409 Conflict, got {status}"

    log("\n=== 9. Payment Capture ===")
    capture_body = {"amount": 350.00, "description": "Full capture of funds"}
    status, data, _ = request("POST", f"/api/v1/payments/{payment_id}/capture", capture_body, headers=api_headers)
    log(f"Capture status: {status}, Payment Status: {data.get('status')}, Captured: {data.get('capturedAmount')}")
    assert status == 200 and data.get("status") == "CAPTURED"

    log("\n=== 10. Payment Refund (Partial) ===")
    refund_body = {"amount": 100.00, "reason": "Customer item exchange"}
    status, data, _ = request("POST", f"/api/v1/payments/{payment_id}/refund", refund_body, headers=api_headers)
    log(f"Refund status: {status}, Payment Status: {data.get('status')}, Refunded: {data.get('refundedAmount')}")
    assert status == 200 and data.get("status") == "PARTIALLY_REFUNDED"

    log("\n=== 11. Query Payment Transaction ===")
    status, data, _ = request("GET", f"/api/v1/payments/{payment_id}", headers=api_headers)
    log(f"Get payment status: {status}, Remaining balance: {float(data['capturedAmount']) - float(data['refundedAmount'])}")
    assert status == 200

    log("\n=== 12. Out-of-Order Webhook Event Handling ===")
    webhook_body = {
        "eventId": f"evt_delayed_{unique}",
        "eventType": "PAYMENT_AUTHORIZED",
        "paymentId": payment_id,
        "sequenceNumber": 1,
        "amount": 350.00
    }
    status, data, _ = request("POST", "/api/v1/webhooks/events", webhook_body)
    log(f"Webhook status: {status}, Result: {data.get('status')}, Notes: {data.get('notes')}")
    assert status == 200
    assert data.get("status") == "IGNORED_OUT_OF_ORDER", f"Expected IGNORED_OUT_OF_ORDER, got {data.get('status')}"

    log("\n=== 13. Audit Logs Inspection ===")
    status, data, _ = request("GET", "/api/v1/audit-logs", headers=jwt_headers)
    log(f"Audit log entries found: {len(data) if isinstance(data, list) else 0}")
    assert status == 200 and len(data) >= 5

    log("\n>>> ALL FUNCTIONAL AND LIFECYCLE TESTS PASSED PERFECTLY! <<<")
    return api_key_secret, jwt_headers

def run_performance_and_rate_limit_tests(api_key_secret):
    log("\n" + "="*50)
    log("=== 14. Rate Limiting Verification ===")
    log("="*50)

    # Make 110 rapid requests to test rate limiting (configured limit is 100/min)
    rate_limited_count = 0
    success_count = 0
    api_headers = {"X-API-KEY": api_key_secret}

    for i in range(120):
        auth_req = {
            "amount": 10.00 + i,
            "currency": "USD",
            "customerEmail": f"user{i}@test.com"
        }
        status, data, hdrs = request("POST", "/api/v1/payments/authorize", auth_req, headers=api_headers)
        if status == 429:
            rate_limited_count += 1
        elif status == 201:
            success_count += 1

    log(f"Rapid 120 calls -> Success (201): {success_count}, Rate Limited (429): {rate_limited_count}")
    assert rate_limited_count > 0, "Rate limiter did not block requests exceeding the quota!"
    log(f"Rate Limiter correctly returned HTTP 429 Too Many Requests after quota was reached!")

    log("\n" + "="*50)
    log("=== 15. Concurrency & Throughput Stress Test ===")
    log("="*50)

    # Concurrency test on public/unlimited endpoint or fresh key
    TOTAL_REQUESTS = 200
    CONCURRENCY = 20

    latencies = []
    statuses = {}
    lock = threading.Lock()

    def worker(worker_id):
        nonlocal latencies, statuses
        t0 = time.time()
        status, _, _ = request("GET", "/actuator/health")
        duration_ms = (time.time() - t0) * 1000.0
        with lock:
            latencies.append(duration_ms)
            statuses[status] = statuses.get(status, 0) + 1

    log(f"Executing {TOTAL_REQUESTS} requests across {CONCURRENCY} concurrent threads...")
    start_time = time.time()
    with ThreadPoolExecutor(max_workers=CONCURRENCY) as executor:
        for i in range(TOTAL_REQUESTS):
            executor.submit(worker, i)

    total_time = time.time() - start_time
    rps = TOTAL_REQUESTS / total_time
    latencies.sort()

    p50 = latencies[int(len(latencies) * 0.50)]
    p95 = latencies[int(len(latencies) * 0.95)]
    p99 = latencies[int(len(latencies) * 0.99)]
    avg_latency = sum(latencies) / len(latencies)
    min_latency = min(latencies)
    max_latency = max(latencies)

    print("\n--- Performance Benchmarks Summary ---")
    print(f"Total Requests:       {TOTAL_REQUESTS}")
    print(f"Concurrency Level:    {CONCURRENCY} concurrent connections")
    print(f"Total Test Duration:  {total_time:.2f} seconds")
    print(f"Throughput (RPS):     {rps:.2f} requests/second")
    print(f"Status Code Breakdown: {statuses}")
    print(f"Min Latency:          {min_latency:.2f} ms")
    print(f"Average Latency:      {avg_latency:.2f} ms")
    print(f"50th Percentile (p50):{p50:.2f} ms")
    print(f"95th Percentile (p95):{p95:.2f} ms")
    print(f"99th Percentile (p99):{p99:.2f} ms")
    print(f"Max Latency:          {max_latency:.2f} ms")
    print("--------------------------------------")

if __name__ == "__main__":
    api_key, _ = run_suite()
    run_performance_and_rate_limit_tests(api_key)
