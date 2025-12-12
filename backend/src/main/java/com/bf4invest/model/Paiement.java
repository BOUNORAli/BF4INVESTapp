package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "paiements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Paiement {
    @Id
    private String id;
    
    private String factureAchatId; // Optionnel
    private String factureVenteId; // Optionnel
    
    private LocalDate date;
    private Double montant;
    private String mode; // virement, cheque, LCN, compensation, especes
    private String reference; // Numéro de chèque, référence virement, etc.
    
    private String notes;
    
    private LocalDateTime createdAt;
}




