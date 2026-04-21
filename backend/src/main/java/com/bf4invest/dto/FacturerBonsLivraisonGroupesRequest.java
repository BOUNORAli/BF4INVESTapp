package com.bf4invest.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class FacturerBonsLivraisonGroupesRequest {
    private List<String> blIds;
    private LocalDate dateFacture;
}
