package com.bf4invest.repository;

import com.bf4invest.model.TransactionBancaire;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionBancaireRepository extends MongoRepository<TransactionBancaire, String> {
    List<TransactionBancaire> findByDateOperationBetween(LocalDate debut, LocalDate fin);
    
    List<TransactionBancaire> findByMappedFalse();
    
    List<TransactionBancaire> findByMappedFalseAndMoisAndAnnee(Integer mois, Integer annee);
    
    List<TransactionBancaire> findByFactureVenteId(String factureVenteId);
    
    List<TransactionBancaire> findByFactureAchatId(String factureAchatId);
    
    List<TransactionBancaire> findByMoisAndAnnee(Integer mois, Integer annee);
    
    List<TransactionBancaire> findByMoisAndAnneeAndMapped(Integer mois, Integer annee, Boolean mapped);
}

