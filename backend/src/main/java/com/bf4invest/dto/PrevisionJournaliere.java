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
public class PrevisionJournaliere {
    private LocalDate date;
    private Double entreesPrevisionnelles;
    private Double sortiesPrevisionnelles;
    private Double soldePrevu;
}




