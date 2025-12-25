package com.bf4invest.repository;

import com.bf4invest.model.Paiement;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PaiementRepository extends MongoRepository<Paiement, String> {
    List<Paiement> findByFactureAchatId(String factureAchatId);
    List<Paiement> findByFactureVenteId(String factureVenteId);
    
    // Nouvelles méthodes pour le calcul TVA au règlement
    List<Paiement> findByDateBetween(LocalDate debut, LocalDate fin);
    
    List<Paiement> findByFactureVenteIdAndDateBetween(String factureVenteId, LocalDate debut, LocalDate fin);
    
    List<Paiement> findByFactureAchatIdAndDateBetween(String factureAchatId, LocalDate debut, LocalDate fin);
}




