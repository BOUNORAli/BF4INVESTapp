package com.bf4invest.repository;

import com.bf4invest.model.Paiement;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaiementRepository extends MongoRepository<Paiement, String> {
    List<Paiement> findByFactureAchatId(String factureAchatId);
    List<Paiement> findByFactureVenteId(String factureVenteId);
}




