package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrevisionPaiement {
    private String id;
    private LocalDate datePrevue;
    private Double montantPrevu;
    private String statut; // PREVU, REALISE, EN_RETARD
    private String notes;
    private LocalDate dateRappel; // Date de rappel optionnelle
    private LocalDateTime createdAt;
}

