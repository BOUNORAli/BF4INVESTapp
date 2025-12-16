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

@Document(collection = "fournisseurs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Supplier {
    @Id
    private String id;
    
    private String nom;
    private String ice;
    private String contact; // Contact principal
    private String adresse;
    private String telephone;
    private String email;
    
    private List<String> modesPaiementAcceptes; // virement, chèque, LCN, compensation
    
    private LocalDate dateRegulariteFiscale; // Date de régularité fiscale du fournisseur
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}




