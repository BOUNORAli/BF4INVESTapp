package com.bf4invest.repository;

import com.bf4invest.model.FactureAchat;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FactureAchatRepository extends MongoRepository<FactureAchat, String> {
    Optional<FactureAchat> findByNumeroFactureAchat(String numero);
    Optional<FactureAchat> findByFichierFactureId(String fichierFactureId);
    List<FactureAchat> findByFournisseurId(String fournisseurId);
    List<FactureAchat> findByEtatPaiement(String etat);
    List<FactureAchat> findByDateEcheanceLessThanEqual(LocalDate date);
    List<FactureAchat> findByDateFactureLessThanEqual(LocalDate date);
    List<FactureAchat> findByBandeCommandeId(String bandeCommandeId);
    List<FactureAchat> findByDateFactureBetween(LocalDate dateDebut, LocalDate dateFin);
}




