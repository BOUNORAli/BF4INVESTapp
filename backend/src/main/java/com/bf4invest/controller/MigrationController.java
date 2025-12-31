package com.bf4invest.controller;

import com.bf4invest.service.DataMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Contrôleur pour les opérations de migration de données
 */
@Slf4j
@RestController
@RequestMapping("/admin/migration")
@RequiredArgsConstructor
public class MigrationController {
    
    private final DataMigrationService migrationService;
    
    /**
     * Synchronise les références BC pour toutes les factures existantes
     * Corrige les factures qui ont un bandeCommandeId mais pas de bcReference
     * 
     * @return Statistiques de la migration
     */
    @PostMapping("/sync-bc-references")
    public ResponseEntity<Map<String, Object>> synchroniserReferencesBC() {
        log.info("🔄 Démarrage de la synchronisation des références BC via API...");
        
        try {
            Map<String, Integer> stats = migrationService.synchroniserReferencesBC();
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Migration terminée avec succès",
                "statistics", stats
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erreur lors de la migration: {}", e.getMessage(), e);
            
            Map<String, Object> response = Map.of(
                "success", false,
                "message", "Erreur lors de la migration: " + e.getMessage()
            );
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
}

