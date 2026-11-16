package com.securepay.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securepay.domain.entity.ApiKey;
import com.securepay.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitingService rateLimitingService;
    private final ObjectMapper objectMapper;

    public RateLimitingFilter(RateLimitingService rateLimitingService, ObjectMapper objectMapper) {
        this.rateLimitingService = rateLimitingService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Apply rate limiting to /api/v1/payments/** and other protected transaction APIs
        if (path.startsWith("/api/v1/payments") || path.startsWith("/api/v1/webhooks")) {
            String limitKey;
            int limitPerMinute = 60;

            ApiKey apiKey = (ApiKey) request.getAttribute("API_KEY_ENTITY");
            if (apiKey != null) {
                limitKey = "apikey:" + apiKey.getKeyPrefix();
                limitPerMinute = apiKey.getRateLimitPerMinute();
            } else {
                String clientIp = request.getHeader("X-Forwarded-For");
                if (clientIp == null || clientIp.isBlank()) {
                    clientIp = request.getRemoteAddr();
                }
                limitKey = "ip:" + clientIp;
            }

            boolean allowed = rateLimitingService.tryConsume(limitKey, limitPerMinute);
            long remaining = rateLimitingService.getAvailableTokens(limitKey, limitPerMinute);
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(remaining));

            if (!allowed) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setHeader("Retry-After", "60");

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        "Too Many Requests",
                        "Rate limit exceeded. Maximum " + limitPerMinute + " requests per minute allowed.",
                        path
                );
                response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
