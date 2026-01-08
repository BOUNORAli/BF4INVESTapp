package com.bf4invest.repository;

import com.bf4invest.model.FactureVente;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FactureVenteRepository extends MongoRepository<FactureVente, String> {
    Optional<FactureVente> findByNumeroFactureVente(String numero);
    List<FactureVente> findByClientId(String clientId);
    List<FactureVente> findByEtatPaiement(String etat);
    List<FactureVente> findByDateEcheanceLessThanEqual(LocalDate date);
    List<FactureVente> findByBandeCommandeId(String bandeCommandeId);
    List<FactureVente> findByDateFactureBetween(LocalDate dateDebut, LocalDate dateFin);
    List<FactureVente> findByBcReference(String bcReference);
    
    // ========== MÉTHODES POUR GESTION DES AVOIRS ==========
    
    // Rechercher tous les avoirs
    List<FactureVente> findByEstAvoirTrue();
    
    // Rechercher les avoirs d'une facture d'origine
    List<FactureVente> findByFactureOrigineId(String factureOrigineId);
    
    // Rechercher par numéro de facture d'origine
    List<FactureVente> findByNumeroFactureOrigine(String numeroFactureOrigine);
    
    // Rechercher les avoirs d'un client
    List<FactureVente> findByClientIdAndEstAvoirTrue(String clientId);
    
    // Rechercher par type de facture
    List<FactureVente> findByTypeFacture(String typeFacture);
}




