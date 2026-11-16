package com.securepay.controller;

import com.securepay.dto.WebhookEventRequest;
import com.securepay.dto.WebhookEventResponse;
import com.securepay.service.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/events")
    public ResponseEntity<WebhookEventResponse> processWebhook(@Valid @RequestBody WebhookEventRequest request,
                                                               HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        WebhookEventResponse response = webhookService.processWebhookEvent(request, ip);
        return ResponseEntity.ok(response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        return (xf != null && !xf.isBlank()) ? xf.split(",")[0] : request.getRemoteAddr();
    }
}
