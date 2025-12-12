package com.bf4invest.config;

import com.bf4invest.model.User;
import com.bf4invest.repository.UserRepository;
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
    
    @Override
    public void run(String... args) {
        // Créer un utilisateur admin par défaut si aucun n'existe
        if (userRepository.findAll().isEmpty()) {
            User admin = User.builder()
                    .name("Administrateur")
                    .email("admin@bf4invest.ma")
                    .password(passwordEncoder.encode("admin123"))
                    .role(User.Role.ADMIN)
                    .enabled(true)
                    .accountNonExpired(true)
                    .accountNonLocked(true)
                    .credentialsNonExpired(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            
            userRepository.save(admin);
            System.out.println("Utilisateur admin créé: admin@bf4invest.ma / admin123");
        }
        
        // Créer aussi un utilisateur avec admin@bf4.com pour compatibilité
        if (userRepository.findByEmail("admin@bf4.com").isEmpty()) {
            User adminAlt = User.builder()
                    .name("Administrateur")
                    .email("admin@bf4.com")
                    .password(passwordEncoder.encode("admin123"))
                    .role(User.Role.ADMIN)
                    .enabled(true)
                    .accountNonExpired(true)
                    .accountNonLocked(true)
                    .credentialsNonExpired(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            
            userRepository.save(adminAlt);
            System.out.println("Utilisateur admin créé: admin@bf4.com / admin123");
        }
    }
}


