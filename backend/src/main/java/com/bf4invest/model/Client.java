package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "clients")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Client {
    @Id
    private String id;
    
    private String ice;
    private String nom;
    private String referenceClient; // Référence client (3 premières lettres du nom par défaut)
    private String adresse;
    private String telephone;
    private String email;
    
    private List<Contact> contacts;
    
    private Double soldeClient; // Solde du client (positif = client nous doit de l'argent)
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Contact {
        private String nom;
        private String tel;
        private String email;
    }
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}




