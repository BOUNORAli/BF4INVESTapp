package com.bf4invest.config;

import com.bf4invest.model.User;
import com.bf4invest.repository.UserRepository;
import com.bf4invest.service.ComptabiliteService;
import com.bf4invest.service.PaymentModeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProdDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PaymentModeService paymentModeService;
    private final ComptabiliteService comptabiliteService;

    @Value("${ADMIN_EMAIL:}")
    private String adminEmail;

    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;

    @Value("${ADMIN_NAME:Administrateur}")
    private String adminName;

    @Override
    public void run(String... args) {
        log.info("ProdDataInitializer: vérification des données initiales...");

        if (userRepository.count() == 0) {
            if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
                log.warn("ProdDataInitializer: Aucun utilisateur en base. Définissez ADMIN_EMAIL et ADMIN_PASSWORD pour créer le premier admin.");
            } else {
                User admin = User.builder()
                        .name(adminName != null && !adminName.isBlank() ? adminName : "Administrateur")
                        .email(adminEmail.trim())
                        .password(passwordEncoder.encode(adminPassword))
                        .role(User.Role.ADMIN)
                        .enabled(true)
                        .accountNonExpired(true)
                        .accountNonLocked(true)
                        .credentialsNonExpired(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                userRepository.save(admin);
                log.info("ProdDataInitializer: Premier administrateur créé: {}", admin.getEmail());
            }
        }

        paymentModeService.initializeDefaultModes();
        comptabiliteService.initializePlanComptable();
        comptabiliteService.getOrCreateCurrentExercice();
        log.info("ProdDataInitializer: modes de paiement et plan comptable initialisés.");
    }
}
