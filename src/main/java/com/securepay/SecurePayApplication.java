package com.securepay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class SecurePayApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurePayApplication.class, args);
    }
}
