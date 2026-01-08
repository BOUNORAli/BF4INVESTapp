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
    List<FactureAchat> findByBcReference(String bcReference);
    
    // ========== MÉTHODES POUR GESTION DES AVOIRS ==========
    
    // Rechercher tous les avoirs
    List<FactureAchat> findByEstAvoirTrue();
    
    // Rechercher les avoirs d'une facture d'origine
    List<FactureAchat> findByFactureOrigineId(String factureOrigineId);
    
    // Rechercher par numéro de facture d'origine
    List<FactureAchat> findByNumeroFactureOrigine(String numeroFactureOrigine);
    
    // Rechercher les avoirs d'un fournisseur
    List<FactureAchat> findByFournisseurIdAndEstAvoirTrue(String fournisseurId);
    
    // Rechercher par type de facture
    List<FactureAchat> findByTypeFacture(String typeFacture);
}




