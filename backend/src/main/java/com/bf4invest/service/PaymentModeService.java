package com.bf4invest.service;

import com.bf4invest.model.PaymentMode;
import com.bf4invest.repository.PaymentModeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentModeService {
    
    private final PaymentModeRepository paymentModeRepository;
    
    /**
     * Initialise les modes de paiement par défaut si la collection est vide
     */
    @Transactional
    public void initializeDefaultModes() {
        if (paymentModeRepository.count() == 0) {
            log.info("Initialisation des modes de paiement par défaut");
            List<PaymentMode> defaultModes = List.of(
                    createDefaultMode("Virement Bancaire"),
                    createDefaultMode("Chèque"),
                    createDefaultMode("Espèces"),
                    createDefaultMode("LCN (Lettre de Change)"),
                    createDefaultMode("Compensation")
            );
            paymentModeRepository.saveAll(defaultModes);
            log.info("{} modes de paiement par défaut créés", defaultModes.size());
        }
    }
    
    private PaymentMode createDefaultMode(String name) {
        LocalDateTime now = LocalDateTime.now();
        return PaymentMode.builder()
                .name(name)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
    
    public List<PaymentMode> findAll() {
        return paymentModeRepository.findAllByOrderByNameAsc();
    }
    
    public List<PaymentMode> findActiveModes() {
        return paymentModeRepository.findByActiveTrueOrderByNameAsc();
    }
    
    public Optional<PaymentMode> findById(String id) {
        return paymentModeRepository.findById(id);
    }
    
    @Transactional
    public PaymentMode create(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Le nom du mode de paiement ne peut pas être vide");
        }
        
        // Vérifier si un mode avec le même nom existe déjà
        List<PaymentMode> existing = paymentModeRepository.findAll();
        boolean exists = existing.stream()
                .anyMatch(m -> m.getName().equalsIgnoreCase(name.trim()));
        
        if (exists) {
            throw new IllegalArgumentException("Un mode de paiement avec ce nom existe déjà");
        }
        
        LocalDateTime now = LocalDateTime.now();
        PaymentMode mode = PaymentMode.builder()
                .name(name.trim())
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        
        PaymentMode saved = paymentModeRepository.save(mode);
        log.info("Mode de paiement créé: {}", saved.getName());
        return saved;
    }
    
    @Transactional
    public PaymentMode update(String id, String name, Boolean active) {
        PaymentMode mode = paymentModeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mode de paiement non trouvé: " + id));
        
        if (name != null && !name.trim().isEmpty()) {
            // Vérifier si un autre mode avec le même nom existe
            List<PaymentMode> existing = paymentModeRepository.findAll();
            boolean exists = existing.stream()
                    .anyMatch(m -> !m.getId().equals(id) && m.getName().equalsIgnoreCase(name.trim()));
            
            if (exists) {
                throw new IllegalArgumentException("Un mode de paiement avec ce nom existe déjà");
            }
            
            mode.setName(name.trim());
        }
        
        if (active != null) {
            mode.setActive(active);
        }
        
        mode.setUpdatedAt(LocalDateTime.now());
        
        PaymentMode saved = paymentModeRepository.save(mode);
        log.info("Mode de paiement mis à jour: {}", saved.getName());
        return saved;
    }
    
    @Transactional
    public void toggleActive(String id) {
        PaymentMode mode = paymentModeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mode de paiement non trouvé: " + id));
        
        mode.setActive(!mode.isActive());
        mode.setUpdatedAt(LocalDateTime.now());
        
        paymentModeRepository.save(mode);
        log.info("Mode de paiement {}: {}", mode.getName(), mode.isActive() ? "activé" : "désactivé");
    }
    
    @Transactional
    public void delete(String id) {
        PaymentMode mode = paymentModeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mode de paiement non trouvé: " + id));
        
        paymentModeRepository.delete(mode);
        log.info("Mode de paiement supprimé: {}", mode.getName());
    }
}

