package com.bf4invest.service;

import com.bf4invest.model.ParametresCalcul;
import com.bf4invest.repository.ParametresCalculRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service pour gérer les paramètres de calcul utilisés dans les formules comptables.
 * Ces paramètres correspondent aux valeurs des cellules $D$2127, $E$2123, $E$2125, $E$2124 de l'Excel.
 */
@Service
@RequiredArgsConstructor
public class ParametresCalculService {
    
    private final ParametresCalculRepository repository;
    
    /**
     * Récupère les paramètres de calcul.
     * Si aucun paramètre n'existe, retourne des valeurs par défaut.
     */
    public ParametresCalcul getParametres() {
        return repository.findFirstByOrderByUpdatedAtDesc()
                .orElseGet(() -> {
                    // Valeurs par défaut si aucun paramètre n'existe
                    ParametresCalcul defaults = ParametresCalcul.builder()
                            .codeDCloture("") // À définir selon votre logique métier
                            .codeEExclu1("") // À définir selon votre logique métier
                            .codeEExclu2("") // À définir selon votre logique métier
                            .codeEExclu3("") // À définir selon votre logique métier
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return repository.save(defaults);
                });
    }
    
    /**
     * Met à jour les paramètres de calcul.
     */
    public ParametresCalcul updateParametres(ParametresCalcul parametres) {
        ParametresCalcul existing = getParametres();
        
        if (parametres.getCodeDCloture() != null) {
            existing.setCodeDCloture(parametres.getCodeDCloture());
        }
        if (parametres.getCodeEExclu1() != null) {
            existing.setCodeEExclu1(parametres.getCodeEExclu1());
        }
        if (parametres.getCodeEExclu2() != null) {
            existing.setCodeEExclu2(parametres.getCodeEExclu2());
        }
        if (parametres.getCodeEExclu3() != null) {
            existing.setCodeEExclu3(parametres.getCodeEExclu3());
        }
        
        existing.setUpdatedAt(LocalDateTime.now());
        return repository.save(existing);
    }
    
    /**
     * Crée ou met à jour les paramètres de calcul.
     */
    public ParametresCalcul saveParametres(ParametresCalcul parametres) {
        Optional<ParametresCalcul> existing = repository.findFirstByOrderByUpdatedAtDesc();
        
        if (existing.isPresent()) {
            ParametresCalcul toUpdate = existing.get();
            toUpdate.setCodeDCloture(parametres.getCodeDCloture());
            toUpdate.setCodeEExclu1(parametres.getCodeEExclu1());
            toUpdate.setCodeEExclu2(parametres.getCodeEExclu2());
            toUpdate.setCodeEExclu3(parametres.getCodeEExclu3());
            toUpdate.setUpdatedAt(LocalDateTime.now());
            return repository.save(toUpdate);
        } else {
            parametres.setCreatedAt(LocalDateTime.now());
            parametres.setUpdatedAt(LocalDateTime.now());
            return repository.save(parametres);
        }
    }
}


