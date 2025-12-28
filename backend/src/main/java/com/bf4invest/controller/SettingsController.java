package com.bf4invest.controller;

import com.bf4invest.dto.CollectionInfo;
import com.bf4invest.dto.DeleteDataRequest;
import com.bf4invest.dto.DeleteDataResponse;
import com.bf4invest.dto.PaymentModeDto;
import com.bf4invest.model.PaymentMode;
import com.bf4invest.service.DataDeletionService;
import com.bf4invest.service.PaymentModeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {
    
    private final PaymentModeService paymentModeService;
    private final DataDeletionService dataDeletionService;
    
    @GetMapping("/payment-modes")
    public ResponseEntity<List<PaymentModeDto>> getPaymentModes() {
        List<PaymentMode> modes = paymentModeService.findAll();
        List<PaymentModeDto> dtos = modes.stream()
                .map(m -> PaymentModeDto.builder()
                        .id(m.getId())
                        .name(m.getName())
                        .active(m.isActive())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
    
    @PostMapping("/payment-modes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentModeDto> createPaymentMode(@RequestBody CreatePaymentModeRequest request) {
        try {
            PaymentMode mode = paymentModeService.create(request.getName());
            PaymentModeDto dto = PaymentModeDto.builder()
                    .id(mode.getId())
                    .name(mode.getName())
                    .active(mode.isActive())
                    .build();
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PutMapping("/payment-modes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentModeDto> updatePaymentMode(
            @PathVariable String id,
            @RequestBody UpdatePaymentModeRequest request) {
        try {
            PaymentMode mode = paymentModeService.update(id, request.getName(), request.getActive());
            PaymentModeDto dto = PaymentModeDto.builder()
                    .id(mode.getId())
                    .name(mode.getName())
                    .active(mode.isActive())
                    .build();
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @PutMapping("/payment-modes/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentModeDto> togglePaymentMode(@PathVariable String id) {
        try {
            paymentModeService.toggleActive(id);
            PaymentMode mode = paymentModeService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Mode non trouvé"));
            PaymentModeDto dto = PaymentModeDto.builder()
                    .id(mode.getId())
                    .name(mode.getName())
                    .active(mode.isActive())
                    .build();
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @DeleteMapping("/payment-modes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePaymentMode(@PathVariable String id) {
        try {
            paymentModeService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    // Endpoints pour la suppression de données
    @GetMapping("/data/collections")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CollectionInfo>> getAvailableCollections() {
        List<CollectionInfo> collections = dataDeletionService.getAvailableCollections();
        return ResponseEntity.ok(collections);
    }
    
    @PostMapping("/data/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeleteDataResponse> deleteAllData(@RequestBody DeleteDataRequest request) {
        // Validation de la confirmation
        if (request.getConfirmation() == null || !request.getConfirmation().equals("SUPPRIMER")) {
            return ResponseEntity.badRequest().build();
        }
        
        // Validation des collections
        if (request.getCollections() == null || request.getCollections().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            DeleteDataResponse response = dataDeletionService.deleteAllData(request.getCollections());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // DTOs pour les requêtes
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreatePaymentModeRequest {
        private String name;
    }
    
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UpdatePaymentModeRequest {
        private String name;
        private Boolean active;
    }
}




