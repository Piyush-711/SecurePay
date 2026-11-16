package com.securepay.security;

import com.securepay.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static MerchantPrincipal getCurrentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof MerchantPrincipal principal) {
            return principal;
        }
        throw new UnauthorizedException("Authenticated merchant principal not found");
    }

    public static UUID getCurrentMerchantId() {
        return getCurrentPrincipal().getMerchantId();
    }
}
