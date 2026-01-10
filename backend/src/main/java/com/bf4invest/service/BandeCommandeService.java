package com.bf4invest.service;

import com.bf4invest.model.*;
import com.bf4invest.repository.BandeCommandeRepository;
import com.bf4invest.util.NumberUtils;
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

                    // Infos livraison (nouveaux champs) - permettre la suppression (null ou chaîne vide)
                    existing.setLieuLivraison(bc.getLieuLivraison() != null && !bc.getLieuLivraison().trim().isEmpty() 
                        ? bc.getLieuLivraison() : null);
                    existing.setConditionLivraison(bc.getConditionLivraison() != null && !bc.getConditionLivraison().trim().isEmpty() 
                        ? bc.getConditionLivraison() : null);
                    existing.setResponsableLivraison(bc.getResponsableLivraison() != null && !bc.getResponsableLivraison().trim().isEmpty() 
                        ? bc.getResponsableLivraison() : null);
                    
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
                    
                    // IMPORTANT: Préserver les totaux Excel si présents (pour aligner avec les factures)
                    if (bc.getTotalAchatTTCFromExcel() != null) {
                        existing.setTotalAchatTTCFromExcel(bc.getTotalAchatTTCFromExcel());
                    }
                    if (bc.getTotalVenteTTCFromExcel() != null) {
                        existing.setTotalVenteTTCFromExcel(bc.getTotalVenteTTCFromExcel());
                    }
                    
                    // Recalculer les totaux (utilisera les totaux Excel si présents)
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
    
    /**
     * Génère un numéro unique pour une Bande de Commande.
     * 
     * Format : refClient + mois + refFournisseur + ordre + "/" + annee2chiffres
     * Exemple : ABC01XYZ01/25 (Client ABC, mois 01, Fournisseur XYZ, ordre 01, année 2025)
     * 
     * IMPORTANT : Le numéro est basé sur le mois/année de la date BC (dateBC), 
     * pas sur la date de création. Cela permet de créer un BC avec une date passée
     * (par exemple, créer en mars un BC oublié de février) tout en conservant 
     * une numérotation séquentielle correcte par mois.
     * 
     * Le comptage se fait uniquement parmi les BC existantes ayant :
     * - Le même mois/année (extrait de dateBC)
     * - Le même fournisseur
     * - Le même client
     * 
     * @param bc La Bande de Commande pour laquelle générer le numéro
     * @return Le numéro BC généré
     * @throws IllegalArgumentException si la date BC, le client ou le fournisseur sont manquants
     */
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
        
        // 4. Extraire mois et année de la date BC
        // IMPORTANT : On utilise la date BC (pas la date de création) pour déterminer le mois/année
        // Cela permet de créer un BC avec une date passée tout en ayant le bon numéro
        final int month = bc.getDateBC().getMonthValue();
        final int year = bc.getDateBC().getYear();
        String mois = String.format("%02d", month);
        String annee2chiffres = String.valueOf(year).substring(2);
        
        // 5. Compter les BC existantes pour ce client + fournisseur + mois + année
        // Le filtrage est strict : même mois, même année, même fournisseur, même client
        // Ce comptage garantit que même si on crée un BC avec une date passée, 
        // le numéro d'ordre sera correct pour ce mois précis
        long count = bcRepository.findAll().stream()
                .filter(existingBc -> {
                    // Filtrer strictement par date BC (mois + année)
                    if (existingBc.getDateBC() == null) return false;
                    if (existingBc.getDateBC().getMonthValue() != month) return false;
                    if (existingBc.getDateBC().getYear() != year) return false;
                    
                    // Filtrer par fournisseur (avec vérification null)
                    if (existingBc.getFournisseurId() == null) return false;
                    if (!existingBc.getFournisseurId().equals(fournisseurId)) return false;
                    
                    // Vérifier si cette BC concerne le même client
                    // Support des deux structures : multi-clients (clientsVente) et rétrocompatibilité (clientId)
                    String existingClientId = null;
                    if (existingBc.getClientsVente() != null && !existingBc.getClientsVente().isEmpty()) {
                        existingClientId = existingBc.getClientsVente().get(0).getClientId();
                    } else if (existingBc.getClientId() != null) {
                        existingClientId = existingBc.getClientId();
                    }
                    
                    // Ne compter que si le client correspond
                    return finalClientId.equals(existingClientId);
                })
                .count();
        
        // 6. Générer le numéro d'ordre : toujours utiliser 2 chiffres (01, 02, 03, etc.)
        // Le comptage est basé uniquement sur le mois/année de la date BC, pas sur la date de création.
        // Cela permet de créer un BC avec une date passée (mois oublié) tout en conservant la numérotation correcte.
        String ordre = String.format("%02d", count + 1);
        
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
     * PRIORITÉ : Utilise les totaux bruts Excel si disponibles, sinon calcule depuis les lignes
     */
    private void calculateTotalsMultiClient(BandeCommande bc) {
        // PRIORITÉ 1 : Vérifier si les totaux Excel sont disponibles
        if (bc.getTotalAchatTTCFromExcel() != null && bc.getTotalAchatTTCFromExcel() != 0 ||
            bc.getTotalVenteTTCFromExcel() != null && bc.getTotalVenteTTCFromExcel() != 0) {
            // Utiliser les totaux Excel et convertir en HT
            calculateBCTotalsFromExcelTotals(bc);
            return;
        }
        
        // PRIORITÉ 2 : Calculer depuis les lignes
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
                
                double ht = NumberUtils.roundTo2Decimals(qte * prix);
                double ttc = NumberUtils.roundTo2Decimals(ht * (1 + tvaRate));
                
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
                        
                        double ht = NumberUtils.roundTo2Decimals(qte * prix);
                        double tva = NumberUtils.roundTo2Decimals(ht * tvaRate);
                        double ttc = NumberUtils.roundTo2Decimals(ht + tva);
                        
                        ligne.setTotalHT(ht);
                        ligne.setTotalTTC(ttc);
                        
                        // Calculer la marge par rapport au prix d'achat
                        Double prixAchat = prixAchatParProduit.get(ligne.getProduitRef());
                        if (prixAchat != null && prixAchat > 0) {
                            double margeUnitaire = NumberUtils.roundTo2Decimals(prix - prixAchat);
                            double margePourcent = NumberUtils.roundTo2Decimals((margeUnitaire / prixAchat) * 100);
                            ligne.setMargeUnitaire(margeUnitaire);
                            ligne.setMargePourcentage(margePourcent);
                            clientMarge += margeUnitaire * qte;
                        }
                        
                        clientVenteHT += ht;
                        clientTVA += tva;
                        clientVenteTTC += ttc;
                    }
                }
                
                // Mettre à jour les totaux du client (arrondis)
                clientVente.setTotalVenteHT(NumberUtils.roundTo2Decimals(clientVenteHT));
                clientVente.setTotalVenteTTC(NumberUtils.roundTo2Decimals(clientVenteTTC));
                clientVente.setTotalTVA(NumberUtils.roundTo2Decimals(clientTVA));
                clientVente.setMargeTotale(NumberUtils.roundTo2Decimals(clientMarge));
                
                if (clientVenteHT > 0 && totalAchatHT > 0) {
                    // Marge en % pour ce client (approximative basée sur la proportion)
                    clientVente.setMargePourcentage(NumberUtils.roundTo2Decimals((clientMarge / clientVenteHT) * 100));
                }
                
                totalVenteHTGlobal += clientVenteHT;
                totalVenteTTCGlobal += clientVenteTTC;
            }
        }
        
        // 3. Mettre à jour les totaux globaux du BC (arrondis)
        bc.setTotalAchatHT(NumberUtils.roundTo2Decimals(totalAchatHT));
        bc.setTotalAchatTTC(NumberUtils.roundTo2Decimals(totalAchatTTC));
        bc.setTotalVenteHT(NumberUtils.roundTo2Decimals(totalVenteHTGlobal));
        bc.setTotalVenteTTC(NumberUtils.roundTo2Decimals(totalVenteTTCGlobal));
        bc.setMargeTotale(NumberUtils.roundTo2Decimals(totalVenteHTGlobal - totalAchatHT));
        
        if (totalAchatHT > 0) {
            bc.setMargePourcentage(NumberUtils.roundTo2Decimals(((totalVenteHTGlobal - totalAchatHT) / totalAchatHT) * 100));
        } else {
            bc.setMargePourcentage(0.0);
        }
    }
    
    /**
     * Calcule les totaux d'une BC depuis les totaux TTC Excel
     * Utilise le taux TVA moyen des lignes pour convertir TTC en HT
     */
    private void calculateBCTotalsFromExcelTotals(BandeCommande bc) {
        // Calculer d'abord les totaux des lignes si nécessaire (pour obtenir le taux TVA moyen)
        // Calculer le taux TVA moyen pondéré depuis les lignes d'achat pour les totaux achat
        double totalHTFromAchatLines = 0.0;
        double totalTVAFromAchatLines = 0.0;
        
        if (bc.getLignesAchat() != null && !bc.getLignesAchat().isEmpty()) {
            for (LigneAchat ligne : bc.getLignesAchat()) {
                // Calculer le total HT de la ligne si pas déjà calculé
                if (ligne.getTotalHT() == null) {
                    double qte = ligne.getQuantiteAchetee() != null ? ligne.getQuantiteAchetee() : 0;
                    double prix = ligne.getPrixAchatUnitaireHT() != null ? ligne.getPrixAchatUnitaireHT() : 0;
                    ligne.setTotalHT(NumberUtils.roundTo2Decimals(qte * prix));
                    if (ligne.getTva() != null) {
                        ligne.setTotalTTC(NumberUtils.roundTo2Decimals(ligne.getTotalHT() * (1 + (ligne.getTva() / 100.0))));
                    }
                }
                
                if (ligne.getTotalHT() != null && ligne.getTva() != null) {
                    totalHTFromAchatLines += ligne.getTotalHT();
                    totalTVAFromAchatLines += NumberUtils.roundTo2Decimals(ligne.getTotalHT() * (ligne.getTva() / 100.0));
                }
            }
        }
        
        // Calculer le taux TVA moyen pondéré depuis les lignes de vente pour les totaux vente
        double totalHTFromVenteLines = 0.0;
        double totalTVAFromVenteLines = 0.0;
        
        if (bc.getClientsVente() != null && !bc.getClientsVente().isEmpty()) {
            for (ClientVente cv : bc.getClientsVente()) {
                if (cv.getLignesVente() != null) {
                    for (LigneVente ligne : cv.getLignesVente()) {
                        // Calculer le total HT de la ligne si pas déjà calculé
                        if (ligne.getTotalHT() == null) {
                            double qte = ligne.getQuantiteVendue() != null ? ligne.getQuantiteVendue() : 0;
                            double prix = ligne.getPrixVenteUnitaireHT() != null ? ligne.getPrixVenteUnitaireHT() : 0;
                            ligne.setTotalHT(NumberUtils.roundTo2Decimals(qte * prix));
                            if (ligne.getTva() != null) {
                                ligne.setTotalTTC(NumberUtils.roundTo2Decimals(ligne.getTotalHT() * (1 + (ligne.getTva() / 100.0))));
                            }
                        }
                        
                        if (ligne.getTotalHT() != null && ligne.getTva() != null) {
                            totalHTFromVenteLines += ligne.getTotalHT();
                            totalTVAFromVenteLines += NumberUtils.roundTo2Decimals(ligne.getTotalHT() * (ligne.getTva() / 100.0));
                        }
                    }
                }
            }
        }
        
        // Calculer les taux TVA moyens
        double tauxTVAAchat = 20.0; // Défaut
        if (totalHTFromAchatLines != 0 && totalTVAFromAchatLines != 0) {
            tauxTVAAchat = (totalTVAFromAchatLines / Math.abs(totalHTFromAchatLines)) * 100.0;
        }
        
        double tauxTVAVente = 20.0; // Défaut
        if (totalHTFromVenteLines != 0 && totalTVAFromVenteLines != 0) {
            tauxTVAVente = (totalTVAFromVenteLines / Math.abs(totalHTFromVenteLines)) * 100.0;
        }
        
        // Utiliser les totaux Excel pour achat si disponible
        if (bc.getTotalAchatTTCFromExcel() != null && bc.getTotalAchatTTCFromExcel() != 0) {
            double totalTTC = Math.abs(bc.getTotalAchatTTCFromExcel());
            double totalHT = NumberUtils.roundTo2Decimals(totalTTC / (1 + (tauxTVAAchat / 100.0)));
            bc.setTotalAchatTTC(NumberUtils.roundTo2Decimals(totalTTC));
            bc.setTotalAchatHT(NumberUtils.roundTo2Decimals(totalHT));
        } else {
            // Calculer depuis les lignes d'achat
            double totalAchatHT = 0.0;
            double totalAchatTTC = 0.0;
            if (bc.getLignesAchat() != null) {
                for (LigneAchat ligne : bc.getLignesAchat()) {
                    if (ligne.getTotalHT() != null) {
                        totalAchatHT += ligne.getTotalHT();
                    }
                    if (ligne.getTotalTTC() != null) {
                        totalAchatTTC += ligne.getTotalTTC();
                    }
                }
            }
            bc.setTotalAchatHT(NumberUtils.roundTo2Decimals(totalAchatHT));
            bc.setTotalAchatTTC(NumberUtils.roundTo2Decimals(totalAchatTTC));
        }
        
        // Utiliser les totaux Excel pour vente si disponible
        if (bc.getTotalVenteTTCFromExcel() != null && bc.getTotalVenteTTCFromExcel() != 0) {
            double totalTTC = Math.abs(bc.getTotalVenteTTCFromExcel());
            double totalHT = NumberUtils.roundTo2Decimals(totalTTC / (1 + (tauxTVAVente / 100.0)));
            bc.setTotalVenteTTC(NumberUtils.roundTo2Decimals(totalTTC));
            bc.setTotalVenteHT(NumberUtils.roundTo2Decimals(totalHT));
        } else {
            // Calculer depuis les lignes de vente
            double totalVenteHT = 0.0;
            double totalVenteTTC = 0.0;
            if (bc.getClientsVente() != null) {
                for (ClientVente cv : bc.getClientsVente()) {
                    if (cv.getTotalVenteHT() != null) {
                        totalVenteHT += cv.getTotalVenteHT();
                    }
                    if (cv.getTotalVenteTTC() != null) {
                        totalVenteTTC += cv.getTotalVenteTTC();
                    }
                }
            }
            bc.setTotalVenteHT(NumberUtils.roundTo2Decimals(totalVenteHT));
            bc.setTotalVenteTTC(NumberUtils.roundTo2Decimals(totalVenteTTC));
        }
        
        // Calculer la marge
        if (bc.getTotalVenteHT() != null && bc.getTotalAchatHT() != null) {
            bc.setMargeTotale(NumberUtils.roundTo2Decimals(bc.getTotalVenteHT() - bc.getTotalAchatHT()));
            if (bc.getTotalAchatHT() > 0) {
                bc.setMargePourcentage(NumberUtils.roundTo2Decimals(((bc.getTotalVenteHT() - bc.getTotalAchatHT()) / bc.getTotalAchatHT()) * 100));
            } else {
                bc.setMargePourcentage(0.0);
            }
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
            
            double htAchat = NumberUtils.roundTo2Decimals(qteAchat * prixAchat);
            double ttcAchat = NumberUtils.roundTo2Decimals(htAchat * (1 + tvaRate));
            
            double htVente = NumberUtils.roundTo2Decimals(qteVente * prixVente);
            double ttcVente = NumberUtils.roundTo2Decimals(htVente * (1 + tvaRate));
            
            ligne.setTotalHT(htVente);
            ligne.setTotalTTC(ttcVente);
            
            if (prixAchat > 0) {
                double margeUnitaire = NumberUtils.roundTo2Decimals(prixVente - prixAchat);
                double margePourcent = NumberUtils.roundTo2Decimals((margeUnitaire / prixAchat) * 100);
                ligne.setMargeUnitaire(margeUnitaire);
                ligne.setMargePourcentage(margePourcent);
            }
            
            totalAchatHT += htAchat;
            totalAchatTTC += ttcAchat;
            totalVenteHT += htVente;
            totalVenteTTC += ttcVente;
        }
        
        bc.setTotalAchatHT(NumberUtils.roundTo2Decimals(totalAchatHT));
        bc.setTotalAchatTTC(NumberUtils.roundTo2Decimals(totalAchatTTC));
        bc.setTotalVenteHT(NumberUtils.roundTo2Decimals(totalVenteHT));
        bc.setTotalVenteTTC(NumberUtils.roundTo2Decimals(totalVenteTTC));
        bc.setMargeTotale(NumberUtils.roundTo2Decimals(totalVenteHT - totalAchatHT));
        
        if (totalAchatHT > 0) {
            bc.setMargePourcentage(NumberUtils.roundTo2Decimals(((totalVenteHT - totalAchatHT) / totalAchatHT) * 100));
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
     * Met à jour le stock des produits à partir des lignes d'achat du BC.
     * 
     * LOGIQUE : Ajoute uniquement le SURPLUS au stock.
     * - Surplus = Quantité achetée - Quantité vendue totale (somme sur tous les clients)
     * - Si pas de ventes ou BC sans clients : ajoute toute la quantité achetée
     * - Si achat > vente : ajoute uniquement le surplus
     * - Si achat = vente : n'ajoute rien au stock
     * 
     * Exemple : Achat 100, Vente Client1=40, Vente Client2=30 → Ajoute 30 au stock (surplus)
     * 
     * Méthode publique pour permettre l'appel depuis ExcelImportService.
     */
    public void updateStockFromBC(BandeCommande bc) {
        if (bc.getLignesAchat() == null || bc.getLignesAchat().isEmpty()) {
            return;
        }
        
        // Vérifier s'il y a des clients avec des ventes
        boolean hasClientVentes = bc.getClientsVente() != null && !bc.getClientsVente().isEmpty();
        
        // Calculer les quantités vendues totales par produit (somme sur tous les clients)
        Map<String, Double> quantiteVendueTotaleParProduit = new HashMap<>();
        if (hasClientVentes) {
            for (ClientVente clientVente : bc.getClientsVente()) {
                if (clientVente.getLignesVente() != null) {
                    for (LigneVente ligneVente : clientVente.getLignesVente()) {
                        if (ligneVente.getProduitRef() != null && !ligneVente.getProduitRef().isEmpty()) {
                            Double quantiteVendue = ligneVente.getQuantiteVendue();
                            if (quantiteVendue != null && quantiteVendue > 0) {
                                quantiteVendueTotaleParProduit.merge(
                                    ligneVente.getProduitRef(), 
                                    quantiteVendue, 
                                    Double::sum
                                );
                            }
                        }
                    }
                }
            }
        }
        
        // Traiter chaque ligne d'achat
        for (LigneAchat ligne : bc.getLignesAchat()) {
            if (ligne.getProduitRef() == null || ligne.getProduitRef().isEmpty()) {
                continue;
            }
            
            Double quantiteAchetee = ligne.getQuantiteAchetee();
            if (quantiteAchetee == null || quantiteAchetee <= 0) {
                continue;
            }
            
            // Calculer le surplus : quantité achetée - quantité vendue totale
            // Si pas de clients ou pas de ventes pour ce produit, quantiteVendueTotale sera 0.0,
            // donc surplus = quantiteAchetee (tout va au stock)
            Double quantiteVendueTotale = quantiteVendueTotaleParProduit.getOrDefault(ligne.getProduitRef(), 0.0);
            Double surplus = quantiteAchetee - quantiteVendueTotale;
            
            // Ajouter au stock uniquement si surplus > 0
            if (surplus > 0) {
                Integer quantiteStock = surplus.intValue(); // Arrondir à l'entier inférieur
                
                try {
                    Product updated = productService.updateStockByRef(ligne.getProduitRef(), quantiteStock);
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
}
