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
@Profile("!prod") // Ne s'exécute PAS en production
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PaymentModeService paymentModeService;
    private final ComptabiliteService comptabiliteService;
    
    @Value("${app.data-initializer.enabled:true}")
    private boolean dataInitializerEnabled;
    
    @Override
    public void run(String... args) {
        if (!dataInitializerEnabled) {
            log.info("DataInitializer désactivé via configuration");
            return;
        }
        
        log.info("Initialisation des données de développement...");
        
        // Créer les comptes utilisateurs avec rôles appropriés (DEV ONLY)
        createUserIfNotExists("Boubker", "boubker@bf4invest.ma", "boubker123", User.Role.ADMIN);
        createUserIfNotExists("Fatima", "fatima@bf4invest.ma", "fatima123", User.Role.COMMERCIAL);
        createUserIfNotExists("Ali", "ali@bf4invest.ma", "ali123", User.Role.COMPTABLE);
        createUserIfNotExists("Direction", "direction@bf4invest.ma", "direction123", User.Role.ADMIN);
        
        // Garder les anciens comptes admin pour compatibilité (DEV ONLY)
        createUserIfNotExists("Administrateur", "admin@bf4invest.ma", "admin123", User.Role.ADMIN);
        createUserIfNotExists("Administrateur", "admin@bf4.com", "admin123", User.Role.ADMIN);
        
        // Initialiser les modes de paiement par défaut
        paymentModeService.initializeDefaultModes();
        
        // Initialiser le plan comptable PCGM
        comptabiliteService.initializePlanComptable();
        
        // Créer l'exercice comptable de l'année en cours
        comptabiliteService.getOrCreateCurrentExercice();
        
        log.info("Initialisation des données terminée");
    }
    
    private void createUserIfNotExists(String name, String email, String password, User.Role role) {
        if (userRepository.findByEmail(email).isEmpty()) {
            User user = User.builder()
                    .name(name)
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .role(role)
                    .enabled(true)
                    .accountNonExpired(true)
                    .accountNonLocked(true)
                    .credentialsNonExpired(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            
            userRepository.save(user);
            // Ne jamais logger les mots de passe - sécurité critique
            log.info("Utilisateur créé: {} avec rôle {}", email, role);
        }
    }
}


