package com.bf4invest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EcheanceDetail {
    private LocalDate date;
    private String type; // VENTE ou ACHAT
    private String numeroFacture;
    private String partenaire;
    private Double montant;
    private String statut; // PREVU, REALISE, EN_RETARD
    private String factureId;
}


