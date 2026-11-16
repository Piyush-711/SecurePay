package com.securepay.security;

import java.io.Serializable;
import java.util.UUID;

public class MerchantPrincipal implements Serializable {
    private final UUID merchantId;
    private final String identifier;
    private final String role;
    private final String apiKeyPrefix;
    private final boolean apiKeyAuth;

    public MerchantPrincipal(UUID merchantId, String identifier, String role, String apiKeyPrefix, boolean apiKeyAuth) {
        this.merchantId = merchantId;
        this.identifier = identifier;
        this.role = role;
        this.apiKeyPrefix = apiKeyPrefix;
        this.apiKeyAuth = apiKeyAuth;
    }

    public static MerchantPrincipal fromJwt(UUID merchantId, String username, String role) {
        return new MerchantPrincipal(merchantId, username, role, null, false);
    }

    public static MerchantPrincipal fromApiKey(UUID merchantId, String keyPrefix, String keyName) {
        return new MerchantPrincipal(merchantId, keyName, "ROLE_MERCHANT_API", keyPrefix, true);
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getRole() {
        return role;
    }

    public String getApiKeyPrefix() {
        return apiKeyPrefix;
    }

    public boolean isApiKeyAuth() {
        return apiKeyAuth;
    }
}
