package com.bf4invest.service;

import com.bf4invest.model.*;
import com.bf4invest.repository.BandeCommandeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BandeCommandeService {
    
    private final BandeCommandeRepository bcRepository;
    private final AuditService auditService;
    private final ProductService productService;
    
    public List<BandeCommande> findAll() {
        return bcRepository.findAll();
    }
    
    public Optional<BandeCommande> findById(String id) {
        return bcRepository.findById(id);
    }
    
    public BandeCommande create(BandeCommande bc) {
        // Générer le numéro BC si non fourni
        if (bc.getNumeroBC() == null || bc.getNumeroBC().isEmpty()) {
            bc.setNumeroBC(generateBCNumber(bc.getDateBC()));
        }
        
        // Calculer les totaux
        calculateTotals(bc);
        
        bc.setCreatedAt(LocalDateTime.now());
        bc.setUpdatedAt(LocalDateTime.now());
        
        BandeCommande saved = bcRepository.save(bc);
        
        // Mettre à jour le stock si demandé
        if (Boolean.TRUE.equals(saved.getAjouterAuStock()) && saved.getLignesAchat() != null) {
            updateStockFromBC(saved);
        }
        
        // Journaliser la création
        int nbClients = saved.getNombreClients();
        String clientsInfo = nbClients > 1 ? " (" + nbClients + " clients)" : (nbClients == 0 ? " (stock)" : "");
        auditService.logCreate("BandeCommande", saved.getId(), 
            "BC " + saved.getNumeroBC() + " créée" + clientsInfo + " - Total: " + saved.getTotalVenteTTC() + " MAD");
        
        return saved;
    }
    
    public BandeCommande update(String id, BandeCommande bc) {
        return bcRepository.findById(id)
                .map(existing -> {
                    String oldEtat = existing.getEtat();
                    
                    existing.setDateBC(bc.getDateBC());
                    existing.setFournisseurId(bc.getFournisseurId());
                    existing.setEtat(bc.getEtat());
                    existing.setNotes(bc.getNotes());
                    
                    if (bc.getModePaiement() != null) {
                        existing.setModePaiement(bc.getModePaiement());
                    }
                    
                    if (bc.getAjouterAuStock() != null) {
                        existing.setAjouterAuStock(bc.getAjouterAuStock());
                    }
                    
                    // Nouvelle structure multi-clients
                    if (bc.getLignesAchat() != null) {
                        existing.setLignesAchat(bc.getLignesAchat());
                    }
                    if (bc.getClientsVente() != null) {
                        existing.setClientsVente(bc.getClientsVente());
                    }
                    
                    // Rétrocompatibilité ancienne structure
                    if (bc.getClientId() != null) {
                        existing.setClientId(bc.getClientId());
                    }
                    if (bc.getLignes() != null) {
                        existing.setLignes(bc.getLignes());
                    }
                    
                    // Recalculer les totaux
                    calculateTotals(existing);
                    
                    existing.setUpdatedAt(LocalDateTime.now());
                    BandeCommande saved = bcRepository.save(existing);
                    
                    // Mettre à jour le stock si demandé (seulement si ajouterAuStock est passé à true)
                    if (Boolean.TRUE.equals(bc.getAjouterAuStock()) && saved.getLignesAchat() != null) {
                        updateStockFromBC(saved);
                    }
                    
                    // Journaliser la modification
                    String details = "BC " + saved.getNumeroBC() + " modifiée";
                    if (oldEtat != null && !oldEtat.equals(bc.getEtat())) {
                        details += " - Statut: " + oldEtat + " -> " + bc.getEtat();
                    }
                    auditService.logUpdate("BandeCommande", saved.getId(), oldEtat, details);
                    
                    return saved;
                })
                .orElseThrow(() -> new RuntimeException("BC not found with id: " + id));
    }
    
    public void delete(String id) {
        // Récupérer la BC avant suppression pour le log
        bcRepository.findById(id).ifPresent(bc -> {
            auditService.logDelete("BandeCommande", id, "BC " + bc.getNumeroBC() + " supprimée");
        });
        bcRepository.deleteById(id);
    }
    
    private String generateBCNumber(LocalDate date) {
        String year = String.valueOf(date.getYear());
        long count = bcRepository.findAll().stream()
                .filter(bc -> bc.getDateBC() != null && bc.getDateBC().getYear() == date.getYear())
                .count();
        String sequence = String.format("%04d", count + 1);
        return String.format("BF4-BC-%s-%s", year, sequence);
    }
    
    /**
     * Calcule tous les totaux du BC (achat + vente par client)
     */
    private void calculateTotals(BandeCommande bc) {
        // Utiliser la nouvelle structure si disponible
        if (bc.isMultiClient()) {
            calculateTotalsMultiClient(bc);
        } else {
            // Rétrocompatibilité avec l'ancienne structure
            calculateTotalsLegacy(bc);
        }
    }
    
    /**
     * Calcul des totaux pour la nouvelle structure multi-clients
     */
    private void calculateTotalsMultiClient(BandeCommande bc) {
        double totalAchatHT = 0.0;
        double totalAchatTTC = 0.0;
        double totalVenteHTGlobal = 0.0;
        double totalVenteTTCGlobal = 0.0;
        
        // Créer une map des prix d'achat par produit pour calculer les marges
        Map<String, Double> prixAchatParProduit = new HashMap<>();
        
        // 1. Calculer les totaux d'achat
        if (bc.getLignesAchat() != null) {
            for (LigneAchat ligne : bc.getLignesAchat()) {
                double qte = ligne.getQuantiteAchetee() != null ? ligne.getQuantiteAchetee() : 0;
                double prix = ligne.getPrixAchatUnitaireHT() != null ? ligne.getPrixAchatUnitaireHT() : 0;
                double tvaRate = ligne.getTva() != null ? ligne.getTva() / 100.0 : 0.0;
                
                double ht = qte * prix;
                double ttc = ht * (1 + tvaRate);
                
                ligne.setTotalHT(ht);
                ligne.setTotalTTC(ttc);
                
                totalAchatHT += ht;
                totalAchatTTC += ttc;
                
                // Stocker le prix d'achat pour calcul des marges
                if (ligne.getProduitRef() != null) {
                    prixAchatParProduit.put(ligne.getProduitRef(), prix);
                }
            }
        }
        
        // 2. Calculer les totaux de vente par client
        if (bc.getClientsVente() != null) {
            for (ClientVente clientVente : bc.getClientsVente()) {
                double clientVenteHT = 0.0;
                double clientVenteTTC = 0.0;
                double clientTVA = 0.0;
                double clientMarge = 0.0;
                
                if (clientVente.getLignesVente() != null) {
                    for (LigneVente ligne : clientVente.getLignesVente()) {
                        double qte = ligne.getQuantiteVendue() != null ? ligne.getQuantiteVendue() : 0;
                        double prix = ligne.getPrixVenteUnitaireHT() != null ? ligne.getPrixVenteUnitaireHT() : 0;
                        double tvaRate = ligne.getTva() != null ? ligne.getTva() / 100.0 : 0.0;
                        
                        double ht = qte * prix;
                        double tva = ht * tvaRate;
                        double ttc = ht + tva;
                        
                        ligne.setTotalHT(ht);
                        ligne.setTotalTTC(ttc);
                        
                        // Calculer la marge par rapport au prix d'achat
                        Double prixAchat = prixAchatParProduit.get(ligne.getProduitRef());
                        if (prixAchat != null && prixAchat > 0) {
                            double margeUnitaire = prix - prixAchat;
                            double margePourcent = (margeUnitaire / prixAchat) * 100;
                            ligne.setMargeUnitaire(margeUnitaire);
                            ligne.setMargePourcentage(margePourcent);
                            clientMarge += margeUnitaire * qte;
                        }
                        
                        clientVenteHT += ht;
                        clientTVA += tva;
                        clientVenteTTC += ttc;
                    }
                }
                
                // Mettre à jour les totaux du client
                clientVente.setTotalVenteHT(clientVenteHT);
                clientVente.setTotalVenteTTC(clientVenteTTC);
                clientVente.setTotalTVA(clientTVA);
                clientVente.setMargeTotale(clientMarge);
                
                if (clientVenteHT > 0 && totalAchatHT > 0) {
                    // Marge en % pour ce client (approximative basée sur la proportion)
                    clientVente.setMargePourcentage((clientMarge / clientVenteHT) * 100);
                }
                
                totalVenteHTGlobal += clientVenteHT;
                totalVenteTTCGlobal += clientVenteTTC;
            }
        }
        
        // 3. Mettre à jour les totaux globaux du BC
        bc.setTotalAchatHT(totalAchatHT);
        bc.setTotalAchatTTC(totalAchatTTC);
        bc.setTotalVenteHT(totalVenteHTGlobal);
        bc.setTotalVenteTTC(totalVenteTTCGlobal);
        bc.setMargeTotale(totalVenteHTGlobal - totalAchatHT);
        
        if (totalAchatHT > 0) {
            bc.setMargePourcentage(((totalVenteHTGlobal - totalAchatHT) / totalAchatHT) * 100);
        } else {
            bc.setMargePourcentage(0.0);
        }
    }
    
    /**
     * Rétrocompatibilité: calcul avec l'ancienne structure (un seul client)
     */
    private void calculateTotalsLegacy(BandeCommande bc) {
        if (bc.getLignes() == null || bc.getLignes().isEmpty()) {
            bc.setTotalAchatHT(0.0);
            bc.setTotalAchatTTC(0.0);
            bc.setTotalVenteHT(0.0);
            bc.setTotalVenteTTC(0.0);
            bc.setMargeTotale(0.0);
            bc.setMargePourcentage(0.0);
            return;
        }
        
        double totalAchatHT = 0.0;
        double totalAchatTTC = 0.0;
        double totalVenteHT = 0.0;
        double totalVenteTTC = 0.0;
        
        for (LineItem ligne : bc.getLignes()) {
            double qteAchat = ligne.getQuantiteAchetee() != null ? ligne.getQuantiteAchetee() : 0;
            double qteVente = ligne.getQuantiteVendue() != null ? ligne.getQuantiteVendue() : 0;
            double prixAchat = ligne.getPrixAchatUnitaireHT() != null ? ligne.getPrixAchatUnitaireHT() : 0;
            double prixVente = ligne.getPrixVenteUnitaireHT() != null ? ligne.getPrixVenteUnitaireHT() : 0;
            double tvaRate = ligne.getTva() != null ? ligne.getTva() / 100.0 : 0.0;
            
            double htAchat = qteAchat * prixAchat;
            double ttcAchat = htAchat * (1 + tvaRate);
            
            double htVente = qteVente * prixVente;
            double ttcVente = htVente * (1 + tvaRate);
            
            ligne.setTotalHT(htVente);
            ligne.setTotalTTC(ttcVente);
            
            if (prixAchat > 0) {
                double margeUnitaire = prixVente - prixAchat;
                double margePourcent = (margeUnitaire / prixAchat) * 100;
                ligne.setMargeUnitaire(margeUnitaire);
                ligne.setMargePourcentage(margePourcent);
            }
            
            totalAchatHT += htAchat;
            totalAchatTTC += ttcAchat;
            totalVenteHT += htVente;
            totalVenteTTC += ttcVente;
        }
        
        bc.setTotalAchatHT(totalAchatHT);
        bc.setTotalAchatTTC(totalAchatTTC);
        bc.setTotalVenteHT(totalVenteHT);
        bc.setTotalVenteTTC(totalVenteTTC);
        bc.setMargeTotale(totalVenteHT - totalAchatHT);
        
        if (totalAchatHT > 0) {
            bc.setMargePourcentage(((totalVenteHT - totalAchatHT) / totalAchatHT) * 100);
        } else {
            bc.setMargePourcentage(0.0);
        }
    }
    
    /**
     * Récupère les lignes de vente pour un client spécifique dans un BC
     * Utile pour la génération de factures
     */
    public List<LigneVente> getLignesVenteForClient(String bcId, String clientId) {
        return bcRepository.findById(bcId)
                .map(bc -> {
                    if (bc.getClientsVente() != null) {
                        return bc.getClientsVente().stream()
                                .filter(cv -> clientId.equals(cv.getClientId()))
                                .findFirst()
                                .map(ClientVente::getLignesVente)
                                .orElse(new ArrayList<>());
                    }
                    return new ArrayList<LigneVente>();
                })
                .orElse(new ArrayList<>());
    }
    
    /**
     * Récupère les totaux de vente pour un client spécifique dans un BC
     */
    public Optional<ClientVente> getClientVenteDetails(String bcId, String clientId) {
        return bcRepository.findById(bcId)
                .flatMap(bc -> {
                    if (bc.getClientsVente() != null) {
                        return bc.getClientsVente().stream()
                                .filter(cv -> clientId.equals(cv.getClientId()))
                                .findFirst();
                    }
                    return Optional.empty();
                });
    }
    
    /**
     * Met à jour le stock des produits à partir des lignes d'achat du BC
     */
    private void updateStockFromBC(BandeCommande bc) {
        if (bc.getLignesAchat() == null || bc.getLignesAchat().isEmpty()) {
            return;
        }
        
        for (LigneAchat ligne : bc.getLignesAchat()) {
            if (ligne.getProduitRef() == null || ligne.getProduitRef().isEmpty()) {
                continue;
            }
            
            Double quantiteDouble = ligne.getQuantiteAchetee();
            if (quantiteDouble == null || quantiteDouble <= 0) {
                continue;
            }
            
            Integer quantite = quantiteDouble.intValue();
            
            try {
                Product updated = productService.updateStockByRef(ligne.getProduitRef(), quantite);
                if (updated != null) {
                    // Log réussi (peut être ajouté si nécessaire)
                } else {
                    // Produit non trouvé - ne pas bloquer
                }
            } catch (Exception e) {
                // Ne pas bloquer la création du BC en cas d'erreur
            }
        }
    }
}
