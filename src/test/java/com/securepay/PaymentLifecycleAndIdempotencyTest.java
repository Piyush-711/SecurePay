package com.securepay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securepay.domain.entity.Merchant;
import com.securepay.domain.entity.PaymentTransaction;
import com.securepay.domain.enums.PaymentStatus;
import com.securepay.dto.AuthorizePaymentRequest;
import com.securepay.dto.CapturePaymentRequest;
import com.securepay.dto.PaymentResponse;
import com.securepay.dto.RefundPaymentRequest;
import com.securepay.repository.MerchantRepository;
import com.securepay.repository.PaymentTransactionRepository;
import com.securepay.service.ApiKeyService;
import com.securepay.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentLifecycleAndIdempotencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private PaymentTransactionRepository paymentRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    private String apiKeySecret;
    private Merchant merchant;

    @BeforeEach
    void setup() {
        merchant = new Merchant("Test Gateway Store", "gateway@store.com");
        merchant = merchantRepository.save(merchant);

        var keyCreated = apiKeyService.createApiKey(
                merchant.getId(),
                new com.securepay.dto.CreateApiKeyRequest("Test Key", 500),
                "admin",
                "127.0.0.1"
        );
        apiKeySecret = keyCreated.secretKey();
    }

    @Test
    @DisplayName("Verify authorization, payload idempotency, capture, and refund cycle")
    void testFullPaymentLifecycleWithIdempotency() throws Exception {
        String idemKey = "idem-" + UUID.randomUUID();
        AuthorizePaymentRequest authReq = new AuthorizePaymentRequest(
                new BigDecimal("250.00"),
                "USD",
                "shopper@domain.com",
                "Order #1001"
        );

        // 1. Initial Authorize with Idempotency Key
        MvcResult firstResult = mockMvc.perform(post("/api/v1/payments/authorize")
                        .header("X-API-KEY", apiKeySecret)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.amount").value(250.00))
                .andReturn();

        PaymentResponse firstPayment = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(),
                PaymentResponse.class
        );
        UUID paymentId = firstPayment.id();

        // 2. Exact Replay: Same key, exact same payload -> returns cached response with Idempotent-Replay header
        mockMvc.perform(post("/api/v1/payments/authorize")
                        .header("X-API-KEY", apiKeySecret)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authReq)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("AUTHORIZED"));

        // 3. Conflicting Replay: Same key, modified payload -> 409 Conflict
        AuthorizePaymentRequest modifiedReq = new AuthorizePaymentRequest(
                new BigDecimal("999.00"),
                "USD",
                "different@domain.com",
                "Modified Order"
        );

        mockMvc.perform(post("/api/v1/payments/authorize")
                        .header("X-API-KEY", apiKeySecret)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(modifiedReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Idempotency Conflict"));

        // 4. Capture Payment
        CapturePaymentRequest captureReq = new CapturePaymentRequest(new BigDecimal("250.00"), "Final Settlement");

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
                        .header("X-API-KEY", apiKeySecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(captureReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURED"))
                .andExpect(jsonPath("$.capturedAmount").value(250.00));

        // 5. Refund Payment
        RefundPaymentRequest refundReq = new RefundPaymentRequest(new BigDecimal("100.00"), "Customer requested partial return");

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refund")
                        .header("X-API-KEY", apiKeySecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refundReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.refundedAmount").value(100.00));
    }
}
