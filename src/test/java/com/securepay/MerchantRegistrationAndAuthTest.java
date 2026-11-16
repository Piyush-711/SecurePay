package com.securepay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securepay.dto.AuthRequest;
import com.securepay.dto.AuthResponse;
import com.securepay.dto.CreateApiKeyRequest;
import com.securepay.dto.MerchantRegisterRequest;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MerchantRegistrationAndAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should successfully register a new merchant, login with JWT, and generate API key")
    void shouldRegisterLoginAndGenerateApiKey() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String businessName = "Fintech Corp " + uniqueSuffix;
        String email = "admin-" + uniqueSuffix + "@fintech.com";
        String username = "admin_" + uniqueSuffix;
        String password = "SuperSecretPassword123!";

        // 1. Merchant Registration
        MerchantRegisterRequest registerRequest = new MerchantRegisterRequest(businessName, email, username, password);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value(businessName))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.active").value(true));

        // 2. Login to get JWT Token
        AuthRequest authRequest = new AuthRequest(username, password);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.role").value("ROLE_MERCHANT_ADMIN"))
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(loginResult.getResponse().getContentAsString(), AuthResponse.class);
        String jwtToken = authResponse.token();
        assertThat(jwtToken).isNotBlank();

        // 3. Create API Key using Bearer JWT
        CreateApiKeyRequest createApiKeyReq = new CreateApiKeyRequest("Production Backend Key", 100);

        MvcResult apiKeyResult = mockMvc.perform(post("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createApiKeyReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secretKey").exists())
                .andExpect(jsonPath("$.keyPrefix").exists())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.rateLimitPerMinute").value(100))
                .andReturn();

        String secretKey = objectMapper.readTree(apiKeyResult.getResponse().getContentAsString()).get("secretKey").asText();
        assertThat(secretKey).startsWith("sec_live_");

        // 4. List API Keys
        mockMvc.perform(get("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Production Backend Key"));

        // 5. Test invalid login
        AuthRequest invalidAuth = new AuthRequest(username, "WrongPassword");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidAuth)))
                .andExpect(status().isUnauthorized());
    }
}
