package com.securepay.controller;

import com.securepay.dto.AuthRequest;
import com.securepay.dto.AuthResponse;
import com.securepay.dto.MerchantRegisterRequest;
import com.securepay.dto.MerchantResponse;
import com.securepay.service.MerchantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final MerchantService merchantService;

    public AuthController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @PostMapping("/register")
    public ResponseEntity<MerchantResponse> register(@Valid @RequestBody MerchantRegisterRequest request,
                                                     HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        MerchantResponse response = merchantService.register(request, ip);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request,
                                              HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        AuthResponse response = merchantService.login(request, ip);
        return ResponseEntity.ok(response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        return (xf != null && !xf.isBlank()) ? xf.split(",")[0] : request.getRemoteAddr();
    }
}
