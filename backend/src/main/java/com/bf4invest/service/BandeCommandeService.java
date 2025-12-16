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
    
    public List<BandeCommande> findAll() {
        return bcRepository.findAll();
    }
    
    public Optional<BandeCommande> findById(String id) {
        return bcRepository.findById(id);
    }
    
    public BandeCommande create(BandeCommande bc) {
        System.out.println("🟢 Service.create() - DÉBUT");
        System.out.println("🟢 Service.create() - BC avant traitement: id=" + bc.getId() + ", numeroBC=" + bc.getNumeroBC());
        System.out.println("🟢 Service.create() - lignesAchat count: " + (bc.getLignesAchat() != null ? bc.getLignesAchat().size() : 0));
        System.out.println("🟢 Service.create() - clientsVente count: " + (bc.getClientsVente() != null ? bc.getClientsVente().size() : 0));
        
        // Générer le numéro BC si non fourni
        if (bc.getNumeroBC() == null || bc.getNumeroBC().isEmpty()) {
            bc.setNumeroBC(generateBCNumber(bc.getDateBC()));
        }
        
        // Calculer les totaux
        calculateTotals(bc);
        
        System.out.println("🟢 Service.create() - Après calculateTotals:");
        System.out.println("🟢 Service.create() - lignesAchat count: " + (bc.getLignesAchat() != null ? bc.getLignesAchat().size() : 0));
        System.out.println("🟢 Service.create() - clientsVente count: " + (bc.getClientsVente() != null ? bc.getClientsVente().size() : 0));
        
        bc.setCreatedAt(LocalDateTime.now());
        bc.setUpdatedAt(LocalDateTime.now());
        
        System.out.println("🟢 Service.create() - Avant save() dans repository");
        BandeCommande saved = bcRepository.save(bc);
        System.out.println("🟢 Service.create() - Après save() dans repository");
        
        System.out.println("🟣 Service.create() - BC sauvegardée:");
        System.out.println("🟣 Service.create() - id: " + saved.getId());
        System.out.println("🟣 Service.create() - lignesAchat count: " + (saved.getLignesAchat() != null ? saved.getLignesAchat().size() : 0));
        System.out.println("🟣 Service.create() - clientsVente count: " + (saved.getClientsVente() != null ? saved.getClientsVente().size() : 0));
        if (saved.getLignesAchat() != null) {
            System.out.println("🟣 Service.create() - lignesAchat: " + saved.getLignesAchat());
        }
        if (saved.getClientsVente() != null) {
            for (int idx = 0; idx < saved.getClientsVente().size(); idx++) {
                var cv = saved.getClientsVente().get(idx);
                System.out.println("🟣 Service.create() - Client " + idx + " (id=" + cv.getClientId() + "): " + (cv.getLignesVente() != null ? cv.getLignesVente().size() : 0) + " lignes");
            }
        }
        
        // Journaliser la création
        int nbClients = saved.getNombreClients();
        String clientsInfo = nbClients > 1 ? " (" + nbClients + " clients)" : "";
        auditService.logCreate("BandeCommande", saved.getId(), 
            "BC " + saved.getNumeroBC() + " créée" + clientsInfo + " - Total: " + saved.getTotalVenteTTC() + " MAD");
        
        return saved;
    }
    
    public BandeCommande update(String id, BandeCommande bc) {
        System.out.println("🟢 Service.update() - DÉBUT - id=" + id);
        System.out.println("🟢 Service.update() - BC reçue: numeroBC=" + bc.getNumeroBC());
        System.out.println("🟢 Service.update() - lignesAchat count: " + (bc.getLignesAchat() != null ? bc.getLignesAchat().size() : 0));
        System.out.println("🟢 Service.update() - clientsVente count: " + (bc.getClientsVente() != null ? bc.getClientsVente().size() : 0));
        
        return bcRepository.findById(id)
                .map(existing -> {
                    System.out.println("🟢 Service.update() - BC existante trouvée: id=" + existing.getId());
                    System.out.println("🟢 Service.update() - BC existante lignesAchat count: " + (existing.getLignesAchat() != null ? existing.getLignesAchat().size() : 0));
                    System.out.println("🟢 Service.update() - BC existante clientsVente count: " + (existing.getClientsVente() != null ? existing.getClientsVente().size() : 0));
                    
                    String oldEtat = existing.getEtat();
                    
                    existing.setDateBC(bc.getDateBC());
                    existing.setFournisseurId(bc.getFournisseurId());
                    existing.setEtat(bc.getEtat());
                    existing.setNotes(bc.getNotes());
                    
                    if (bc.getModePaiement() != null) {
                        existing.setModePaiement(bc.getModePaiement());
                    }
                    
                    // Nouvelle structure multi-clients
                    if (bc.getLignesAchat() != null) {
                        System.out.println("🟢 Service.update() - Mise à jour lignesAchat: " + bc.getLignesAchat().size() + " lignes");
                        existing.setLignesAchat(bc.getLignesAchat());
                    } else {
                        System.out.println("⚠️ Service.update() - lignesAchat est NULL dans BC reçue!");
                    }
                    if (bc.getClientsVente() != null) {
                        System.out.println("🟢 Service.update() - Mise à jour clientsVente: " + bc.getClientsVente().size() + " clients");
                        for (int idx = 0; idx < bc.getClientsVente().size(); idx++) {
                            var cv = bc.getClientsVente().get(idx);
                            System.out.println("🟢 Service.update() - Client " + idx + " (id=" + cv.getClientId() + "): " + (cv.getLignesVente() != null ? cv.getLignesVente().size() : 0) + " lignes");
                        }
                        existing.setClientsVente(bc.getClientsVente());
                    } else {
                        System.out.println("⚠️ Service.update() - clientsVente est NULL dans BC reçue!");
                    }
                    
                    // Rétrocompatibilité ancienne structure
                    if (bc.getClientId() != null) {
                        existing.setClientId(bc.getClientId());
                    }
                    if (bc.getLignes() != null) {
                        existing.setLignes(bc.getLignes());
                    }
                    
                    System.out.println("🟢 Service.update() - Avant calculateTotals:");
                    System.out.println("🟢 Service.update() - existing.lignesAchat count: " + (existing.getLignesAchat() != null ? existing.getLignesAchat().size() : 0));
                    System.out.println("🟢 Service.update() - existing.clientsVente count: " + (existing.getClientsVente() != null ? existing.getClientsVente().size() : 0));
                    
                    // Recalculer les totaux
                    calculateTotals(existing);
                    
                    System.out.println("🟢 Service.update() - Après calculateTotals:");
                    System.out.println("🟢 Service.update() - existing.lignesAchat count: " + (existing.getLignesAchat() != null ? existing.getLignesAchat().size() : 0));
                    System.out.println("🟢 Service.update() - existing.clientsVente count: " + (existing.getClientsVente() != null ? existing.getClientsVente().size() : 0));
                    
                    existing.setUpdatedAt(LocalDateTime.now());
                    System.out.println("🟢 Service.update() - Avant save() dans repository");
                    BandeCommande saved = bcRepository.save(existing);
                    System.out.println("🟢 Service.update() - Après save() dans repository");
                    
                    System.out.println("🟣 Service.update() - BC sauvegardée:");
                    System.out.println("🟣 Service.update() - id: " + saved.getId());
                    System.out.println("🟣 Service.update() - lignesAchat count: " + (saved.getLignesAchat() != null ? saved.getLignesAchat().size() : 0));
                    System.out.println("🟣 Service.update() - clientsVente count: " + (saved.getClientsVente() != null ? saved.getClientsVente().size() : 0));
                    if (saved.getLignesAchat() != null) {
                        System.out.println("🟣 Service.update() - lignesAchat: " + saved.getLignesAchat());
                    }
                    if (saved.getClientsVente() != null) {
                        for (int idx = 0; idx < saved.getClientsVente().size(); idx++) {
                            var cv = saved.getClientsVente().get(idx);
                            System.out.println("🟣 Service.update() - Client " + idx + " (id=" + cv.getClientId() + "): " + (cv.getLignesVente() != null ? cv.getLignesVente().size() : 0) + " lignes");
                        }
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
}
