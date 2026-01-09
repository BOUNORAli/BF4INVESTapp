package com.bf4invest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiPartnerSituationRequest {
    private List<String> clientIds; // Pour les clients
    private List<String> supplierIds; // Pour les fournisseurs
    private LocalDate from;
    private LocalDate to;
}
