package com.bf4invest.config;

import com.bf4invest.model.User;
import com.bf4invest.repository.UserRepository;
import com.bf4invest.service.PaymentModeService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PaymentModeService paymentModeService;
    
    @Override
    public void run(String... args) {
        // Créer les 4 comptes utilisateurs si ils n'existent pas
        createUserIfNotExists("Boubker", "boubker@bf4invest.ma", "boubker123");
        createUserIfNotExists("Fatima", "fatima@bf4invest.ma", "fatima123");
        createUserIfNotExists("Ali", "ali@bf4invest.ma", "ali123");
        createUserIfNotExists("Direction", "direction@bf4invest.ma", "direction123");
        
        // Garder les anciens comptes admin pour compatibilité
        createUserIfNotExists("Administrateur", "admin@bf4invest.ma", "admin123");
        createUserIfNotExists("Administrateur", "admin@bf4.com", "admin123");
        
        // Initialiser les modes de paiement par défaut
        paymentModeService.initializeDefaultModes();
    }
    
    private void createUserIfNotExists(String name, String email, String password) {
        if (userRepository.findByEmail(email).isEmpty()) {
            User user = User.builder()
                    .name(name)
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .role(User.Role.ADMIN)
                    .enabled(true)
                    .accountNonExpired(true)
                    .accountNonLocked(true)
                    .credentialsNonExpired(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            
            userRepository.save(user);
            System.out.println("Utilisateur cree: " + email + " / " + password);
        }
    }
}


