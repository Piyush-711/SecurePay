package com.securepay.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securepay.domain.entity.IdempotencyRecord;
import com.securepay.domain.enums.IdempotencyStatus;
import com.securepay.dto.ErrorResponse;
import com.securepay.exception.IdempotencyConflictException;
import com.securepay.security.MerchantPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);

        // Only enforce on mutating requests (POST, PUT, PATCH) when header is provided
        if (idempotencyKey == null || idempotencyKey.isBlank() ||
                !(request.getMethod().equalsIgnoreCase("POST") || request.getMethod().equalsIgnoreCase("PUT"))) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof MerchantPrincipal principal)) {
            // Not authenticated yet or unauthenticated endpoint; proceed
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        String payload = wrappedRequest.getBodyAsString();
        String path = request.getRequestURI();
        String method = request.getMethod();

        IdempotencyRecord record;
        try {
            record = idempotencyService.checkOrStart(principal.getMerchantId(), idempotencyKey.trim(), path, method, payload);
        } catch (IdempotencyConflictException ex) {
            response.setStatus(HttpStatus.CONFLICT.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorResponse error = new ErrorResponse(
                    HttpStatus.CONFLICT.value(),
                    "Idempotency Conflict",
                    ex.getMessage(),
                    path
            );
            response.getWriter().write(objectMapper.writeValueAsString(error));
            return;
        }

        // If record is already completed, return cached response
        if (record != null && record.getStatus() == IdempotencyStatus.COMPLETED) {
            response.setStatus(record.getResponseStatus() != null ? record.getResponseStatus() : 200);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Idempotent-Replay", "true");
            if (record.getResponseBody() != null) {
                response.getWriter().write(record.getResponseBody());
            }
            return;
        }

        // Execute downstream chain
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);

            int status = wrappedResponse.getStatus();
            byte[] responseBytes = wrappedResponse.getContentAsByteArray();
            String responseBody = new String(responseBytes, StandardCharsets.UTF_8);

            if (status >= 200 && status < 300) {
                idempotencyService.recordCompletion(principal.getMerchantId(), idempotencyKey.trim(), status, responseBody);
            } else {
                idempotencyService.recordFailure(principal.getMerchantId(), idempotencyKey.trim(), status, responseBody);
            }

            wrappedResponse.copyBodyToResponse();
        } catch (Exception ex) {
            idempotencyService.recordFailure(principal.getMerchantId(), idempotencyKey.trim(), 500, ex.getMessage());
            throw ex;
        }
    }
}
