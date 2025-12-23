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
    private final ClientService clientService;
    private final SupplierService supplierService;
    
    public List<BandeCommande> findAll() {
        return bcRepository.findAll();
    }
    
    public Optional<BandeCommande> findById(String id) {
        return bcRepository.findById(id);
    }
    
    public BandeCommande create(BandeCommande bc) {
        // Générer le numéro BC si non fourni
        if (bc.getNumeroBC() == null || bc.getNumeroBC().isEmpty()) {
            bc.setNumeroBC(generateBCNumber(bc));
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
                    
                    if (bc.getDelaiPaiement() != null) {
                        existing.setDelaiPaiement(bc.getDelaiPaiement());
                    }

                    // Infos livraison (nouveaux champs)
                    if (bc.getLieuLivraison() != null) {
                        existing.setLieuLivraison(bc.getLieuLivraison());
                    }
                    if (bc.getConditionLivraison() != null) {
                        existing.setConditionLivraison(bc.getConditionLivraison());
                    }
                    if (bc.getResponsableLivraison() != null) {
                        existing.setResponsableLivraison(bc.getResponsableLivraison());
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
    
    private String generateBCNumber(BandeCommande bc) {
        if (bc.getDateBC() == null) {
            throw new IllegalArgumentException("La date BC est requise pour générer le numéro");
        }
        
        // 1. Récupérer le client (premier de clientsVente ou clientId pour rétrocompatibilité)
        String clientId = null;
        if (bc.getClientsVente() != null && !bc.getClientsVente().isEmpty()) {
            clientId = bc.getClientsVente().get(0).getClientId();
        } else if (bc.getClientId() != null) {
            clientId = bc.getClientId();
        }
        
        if (clientId == null) {
            throw new IllegalArgumentException("Un client est requis pour générer le numéro BC");
        }
        
        // Créer une variable finale pour utiliser dans les lambdas
        final String finalClientId = clientId;
        
        // 2. Récupérer la référence du client
        Client client = clientService.findById(finalClientId)
                .orElseThrow(() -> new IllegalArgumentException("Client non trouvé: " + finalClientId));
        
        String refClient = client.getReferenceClient();
        if (refClient == null || refClient.trim().isEmpty()) {
            // Générer depuis le nom si manquant et sauvegarder
            refClient = generateReferenceFromName(client.getNom());
            client.setReferenceClient(refClient);
            clientService.update(finalClientId, client); // Sauvegarder la référence
        }
        
        // 3. Récupérer la référence du fournisseur
        if (bc.getFournisseurId() == null) {
            throw new IllegalArgumentException("Un fournisseur est requis pour générer le numéro BC");
        }
        
        // Créer une variable finale pour le fournisseurId
        final String fournisseurId = bc.getFournisseurId();
        
        Supplier supplier = supplierService.findById(fournisseurId)
                .orElseThrow(() -> new IllegalArgumentException("Fournisseur non trouvé: " + fournisseurId));
        
        String refFournisseur = supplier.getReferenceFournisseur();
        if (refFournisseur == null || refFournisseur.trim().isEmpty()) {
            // Générer depuis le nom si manquant et sauvegarder
            refFournisseur = generateReferenceFromName(supplier.getNom());
            supplier.setReferenceFournisseur(refFournisseur);
            supplierService.update(fournisseurId, supplier); // Sauvegarder la référence
        }
        
        // 4. Extraire mois et année
        final int month = bc.getDateBC().getMonthValue();
        final int year = bc.getDateBC().getYear();
        String mois = String.format("%02d", month);
        String annee2chiffres = String.valueOf(year).substring(2);
        
        // 5. Compter les BC existantes pour ce client + fournisseur + mois + année
        long count = bcRepository.findAll().stream()
                .filter(existingBc -> {
                    if (existingBc.getDateBC() == null) return false;
                    if (existingBc.getDateBC().getMonthValue() != month) return false;
                    if (existingBc.getDateBC().getYear() != year) return false;
                    if (!existingBc.getFournisseurId().equals(fournisseurId)) return false;
                    
                    // Vérifier si cette BC concerne le même client
                    String existingClientId = null;
                    if (existingBc.getClientsVente() != null && !existingBc.getClientsVente().isEmpty()) {
                        existingClientId = existingBc.getClientsVente().get(0).getClientId();
                    } else if (existingBc.getClientId() != null) {
                        existingClientId = existingBc.getClientId();
                    }
                    
                    return finalClientId.equals(existingClientId);
                })
                .count();
        
        // 6. Générer le numéro d'ordre : "01" si première, sinon "2", "3", etc.
        String ordre;
        if (count == 0) {
            ordre = "01";
        } else {
            ordre = String.valueOf(count + 1);
        }
        
        // 7. Assembler : refClient + mois + refFournisseur + ordre + "/" + annee2chiffres
        return refClient + mois + refFournisseur + ordre + "/" + annee2chiffres;
    }
    
    /**
     * Génère une référence à partir des 3 premières lettres du nom
     */
    private String generateReferenceFromName(String nom) {
        if (nom == null || nom.trim().isEmpty()) {
            return "XXX";
        }
        
        // Supprimer les accents et convertir en majuscules
        String normalized = normalizeReference(nom);
        
        // Prendre les 3 premiers caractères (lettres uniquement)
        StringBuilder ref = new StringBuilder();
        for (char c : normalized.toCharArray()) {
            if (Character.isLetter(c)) {
                ref.append(c);
                if (ref.length() >= 3) {
                    break;
                }
            }
        }
        
        // Si moins de 3 lettres, compléter avec X
        while (ref.length() < 3) {
            ref.append('X');
        }
        
        return ref.toString().substring(0, 3).toUpperCase();
    }
    
    /**
     * Normalise une référence : supprime accents, espaces, convertit en majuscules
     */
    private String normalizeReference(String reference) {
        if (reference == null) {
            return "";
        }
        
        // Supprimer les accents
        String normalized = java.text.Normalizer.normalize(reference, java.text.Normalizer.Form.NFD);
        normalized = normalized.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        
        // Supprimer les espaces et caractères spéciaux, garder seulement lettres et chiffres
        normalized = normalized.replaceAll("[^a-zA-Z0-9]", "");
        
        return normalized.toUpperCase();
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
