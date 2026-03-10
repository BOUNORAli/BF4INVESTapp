package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "declarations_is")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeclarationIS {
    @Id
    private String id;

    private Integer annee;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private String exerciceId;

    private Double resultatComptable;
    private List<AjustementFiscal> reintegrations;
    private List<AjustementFiscal> deductions;
    private Double totalReintegrations;
    private Double totalDeductions;
    private Double resultatFiscal;

    private Double chiffreAffaires;
    private Double isCalcule;
    private Double cotisationMinimale;
    private Double isDu;
    private Double acomptesPayes;
    private Double reliquat;
    private Double excedent;

    private StatutDeclaration statut;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum StatutDeclaration {
        BROUILLON,
        VALIDEE
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AjustementFiscal {
        private String libelle;
        private Double montant;
    }
}
