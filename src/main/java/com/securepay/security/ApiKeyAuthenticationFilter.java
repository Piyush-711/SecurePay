package com.securepay.security;

import com.securepay.domain.entity.ApiKey;
import com.securepay.domain.enums.ApiKeyStatus;
import com.securepay.repository.ApiKeyRepository;
import com.securepay.util.HashUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-KEY";

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyAuthenticationFilter(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKeyHeader = request.getHeader(API_KEY_HEADER);

        if (apiKeyHeader != null && !apiKeyHeader.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
            String keyHash = HashUtils.sha256(apiKeyHeader.trim());
            Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByKeyHashAndStatus(keyHash, ApiKeyStatus.ACTIVE);

            if (apiKeyOpt.isPresent()) {
                ApiKey apiKey = apiKeyOpt.get();
                boolean notExpired = apiKey.getExpiresAt() == null || apiKey.getExpiresAt().isAfter(Instant.now());
                boolean merchantActive = apiKey.getMerchant().isActive();

                if (notExpired && merchantActive) {
                    MerchantPrincipal principal = MerchantPrincipal.fromApiKey(
                            apiKey.getMerchant().getId(),
                            apiKey.getKeyPrefix(),
                            apiKey.getName()
                    );

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_MERCHANT_API"), new SimpleGrantedAuthority("ROLE_MERCHANT_OPERATOR"))
                    );

                    request.setAttribute("API_KEY_ENTITY", apiKey);
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
