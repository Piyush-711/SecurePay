package com.securepay.repository;

import com.securepay.domain.entity.MerchantUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantUserRepository extends JpaRepository<MerchantUser, UUID> {
    Optional<MerchantUser> findByUsername(String username);
    boolean existsByUsername(String username);
}
