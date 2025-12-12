package com.bf4invest.repository;

import com.bf4invest.model.BandeCommande;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BandeCommandeRepository extends MongoRepository<BandeCommande, String> {
    Optional<BandeCommande> findByNumeroBC(String numeroBC);
    List<BandeCommande> findByClientId(String clientId);
    List<BandeCommande> findByFournisseurId(String fournisseurId);
    List<BandeCommande> findByDateBCBetween(LocalDate start, LocalDate end);
    List<BandeCommande> findByEtat(String etat);
}




