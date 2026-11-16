package com.securepay.service;

import com.securepay.domain.entity.Merchant;
import com.securepay.domain.entity.MerchantUser;
import com.securepay.domain.enums.Role;
import com.securepay.dto.AuthRequest;
import com.securepay.dto.AuthResponse;
import com.securepay.dto.MerchantRegisterRequest;
import com.securepay.dto.MerchantResponse;
import com.securepay.exception.BadRequestException;
import com.securepay.exception.ResourceNotFoundException;
import com.securepay.exception.UnauthorizedException;
import com.securepay.repository.MerchantRepository;
import com.securepay.repository.MerchantUserRepository;
import com.securepay.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final MerchantUserRepository merchantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditLogService auditLogService;

    public MerchantService(MerchantRepository merchantRepository,
                           MerchantUserRepository merchantUserRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider,
                           AuditLogService auditLogService) {
        this.merchantRepository = merchantRepository;
        this.merchantUserRepository = merchantUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public MerchantResponse register(MerchantRegisterRequest request, String ipAddress) {
        if (merchantRepository.existsByEmail(request.email())) {
            throw new BadRequestException("Merchant with email '" + request.email() + "' already exists");
        }
        if (merchantRepository.existsByName(request.businessName())) {
            throw new BadRequestException("Merchant business name '" + request.businessName() + "' is already registered");
        }
        if (merchantUserRepository.existsByUsername(request.username())) {
            throw new BadRequestException("Username '" + request.username() + "' is already taken");
        }

        Merchant merchant = new Merchant(request.businessName(), request.email());
        merchant = merchantRepository.save(merchant);

        MerchantUser adminUser = new MerchantUser(
                merchant,
                request.username(),
                passwordEncoder.encode(request.password()),
                Role.ROLE_MERCHANT_ADMIN
        );
        merchantUserRepository.save(adminUser);

        auditLogService.record(
                merchant.getId(),
                request.username(),
                "MERCHANT_REGISTER",
                "MERCHANT",
                merchant.getId().toString(),
                ipAddress,
                201,
                0
        );

        return new MerchantResponse(
                merchant.getId(),
                merchant.getName(),
                merchant.getEmail(),
                merchant.isActive(),
                merchant.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(AuthRequest request, String ipAddress) {
        MerchantUser user = merchantUserRepository.findByUsername(request.username())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        if (!user.isEnabled() || !user.getMerchant().isActive()) {
            throw new UnauthorizedException("Account is disabled or merchant is inactive");
        }

        String token = jwtTokenProvider.generateToken(
                user.getMerchant().getId(),
                user.getUsername(),
                user.getRole()
        );

        auditLogService.record(
                user.getMerchant().getId(),
                user.getUsername(),
                "USER_LOGIN",
                "AUTH",
                user.getId().toString(),
                ipAddress,
                200,
                0
        );

        return new AuthResponse(
                token,
                user.getMerchant().getId(),
                user.getUsername(),
                user.getRole().name(),
                jwtTokenProvider.getExpirationMs()
        );
    }

    @Transactional(readOnly = true)
    public MerchantResponse getMerchant(UUID id) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found with id: " + id));
        return new MerchantResponse(
                merchant.getId(),
                merchant.getName(),
                merchant.getEmail(),
                merchant.isActive(),
                merchant.getCreatedAt()
        );
    }
}
