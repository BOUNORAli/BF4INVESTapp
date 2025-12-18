package com.bf4invest.service;

import com.bf4invest.model.*;
import com.bf4invest.repository.BandeCommandeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Service de calcul comptable basé sur les formules Excel.
 * Implémente toutes les formules de calcul des colonnes Excel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalculComptableService {
    
    private final ParametresCalculService parametresCalculService;
    private final BandeCommandeRepository bcRepository;
    
    /**
     * Calcule tous les champs comptables pour une FactureVente.
     */
    public void calculerFactureVente(FactureVente facture) {
        if (facture == null) {
            return;
        }
        
        // Récupérer la référence BC si bandeCommandeId est présent
        if (facture.getBandeCommandeId() != null && facture.getBcReference() == null) {
            Optional<BandeCommande> bc = bcRepository.findById(facture.getBandeCommandeId());
            if (bc.isPresent() && bc.get().getNumeroBC() != null) {
                facture.setBcReference(bc.get().getNumeroBC());
            }
        }
        
        // Initialiser les valeurs par défaut si nécessaire
        if (facture.getTvaRate() == null) {
            // Calculer le taux de TVA à partir des totaux si disponibles
            if (facture.getTotalHT() != null && facture.getTotalHT() > 0 && 
                facture.getTotalTTC() != null && facture.getTotalTTC() > 0) {
                double tvaAmount = facture.getTotalTTC() - facture.getTotalHT();
                facture.setTvaRate(tvaAmount / facture.getTotalHT());
            } else {
                facture.setTvaRate(0.20); // 20% par défaut
            }
        }
        if (facture.getTauxRG() == null) {
            facture.setTauxRG(0.0); // Pas de remise par défaut
        }
        if (facture.getTypeMouvement() == null) {
            facture.setTypeMouvement("C"); // Client par défaut
        }
        if (facture.getNature() == null) {
            facture.setNature("facture");
        }
        
        // Calculer tous les champs
        facture.setTvaMois(calculerTvaMois(facture.getDateFacture()));
        facture.setSolde(calculerSolde(facture));
        facture.setTotalTTCApresRG(calculerTotalTTCApresRG(facture));
        facture.setTotalTTCApresRG_SIGNE(calculerTotalTTCApresRG_SIGNE(facture));
        facture.setTotalPaiementTTC(calculerTotalPaiementTTC(facture));
        facture.setRgTTC(calculerRgTTC(facture));
        facture.setRgHT(calculerRgHT(facture));
        facture.setFactureHT_YC_RG(calculerFactureHT_YC_RG(facture));
        facture.setHtPaye(calculerHtPaye(facture));
        facture.setTvaFactureYcRg(calculerTvaFactureYcRg(facture));
        facture.setTvaPaye(calculerTvaPaye(facture));
        facture.setBilan(calculerBilan(facture));
    }
    
    /**
     * Calcule tous les champs comptables pour une FactureAchat.
     */
    public void calculerFactureAchat(FactureAchat facture) {
        if (facture == null) {
            return;
        }
        
        // Récupérer la référence BC si bandeCommandeId est présent
        if (facture.getBandeCommandeId() != null && facture.getBcReference() == null) {
            Optional<BandeCommande> bc = bcRepository.findById(facture.getBandeCommandeId());
            if (bc.isPresent() && bc.get().getNumeroBC() != null) {
                facture.setBcReference(bc.get().getNumeroBC());
            }
        }
        
        // Initialiser les valeurs par défaut si nécessaire
        if (facture.getTvaRate() == null) {
            // Calculer le taux de TVA à partir des totaux si disponibles
            if (facture.getTotalHT() != null && facture.getTotalHT() > 0 && 
                facture.getTotalTTC() != null && facture.getTotalTTC() > 0) {
                double tvaAmount = facture.getTotalTTC() - facture.getTotalHT();
                facture.setTvaRate(tvaAmount / facture.getTotalHT());
            } else {
                facture.setTvaRate(0.20); // 20% par défaut
            }
        }
        if (facture.getTauxRG() == null) {
            facture.setTauxRG(0.0); // Pas de remise par défaut
        }
        if (facture.getTypeMouvement() == null) {
            facture.setTypeMouvement("F"); // Fournisseur par défaut
        }
        if (facture.getNature() == null) {
            facture.setNature("facture");
        }
        
        // Calculer tous les champs
        facture.setTvaMois(calculerTvaMois(facture.getDateFacture()));
        facture.setSolde(calculerSolde(facture));
        facture.setTotalTTCApresRG(calculerTotalTTCApresRG(facture));
        facture.setTotalTTCApresRG_SIGNE(calculerTotalTTCApresRG_SIGNE(facture));
        facture.setTotalPaiementTTC(calculerTotalPaiementTTC(facture));
        facture.setRgTTC(calculerRgTTC(facture));
        facture.setRgHT(calculerRgHT(facture));
        facture.setFactureHT_YC_RG(calculerFactureHT_YC_RG(facture));
        facture.setHtPaye(calculerHtPaye(facture));
        facture.setTvaFactureYcRg(calculerTvaFactureYcRg(facture));
        facture.setTvaPaye(calculerTvaPaye(facture));
        facture.setBilan(calculerBilan(facture));
    }
    
    /**
     * Calcule tous les champs comptables pour un Paiement.
     */
    public void calculerPaiement(Paiement paiement) {
        if (paiement == null) {
            return;
        }
        
        // Initialiser les valeurs par défaut si nécessaire
        if (paiement.getTvaRate() == null) {
            paiement.setTvaRate(0.20); // 20% par défaut
        }
        if (paiement.getTypeMouvement() == null) {
            // Déterminer le type selon la facture associée
            if (paiement.getFactureVenteId() != null) {
                paiement.setTypeMouvement("C"); // Client
            } else if (paiement.getFactureAchatId() != null) {
                paiement.setTypeMouvement("F"); // Fournisseur
            }
        }
        if (paiement.getNature() == null) {
            paiement.setNature("paiement");
        }
        
        // Calculer tous les champs
        paiement.setTotalPaiementTTC(calculerTotalPaiementTTC(paiement));
        paiement.setHtPaye(calculerHtPaye(paiement));
        paiement.setTvaPaye(calculerTvaPaye(paiement));
    }
    
    // ========== IMPLÉMENTATION DES FORMULES EXCEL ==========
    
    /**
     * Formule: =SI(V7="";"";S7&"/"&R7)
     * TVA Mois: Format "mois/année" (ex: "01/2025")
     * V7 = totalPaiementTTC, S7 = mois, R7 = année
     */
    private String calculerTvaMois(LocalDate date) {
        if (date == null) {
            return "";
        }
        int mois = date.getMonthValue();
        int annee = date.getYear();
        return String.format("%02d/%d", mois, annee);
    }
    
    /**
     * Formule: =SI(E7="C";L7;SI(E7<>"IB";-L7;SI(ET(E7="IB";F7=B7);-L7;L7)))
     * Solde: Signe selon le type de mouvement
     * E7 = typeMouvement, L7 = montant (totalTTC), F7 = colF, B7 = colB
     */
    private Double calculerSolde(FactureVente facture) {
        String typeMouvement = facture.getTypeMouvement();
        Double montant = facture.getTotalTTC();
        
        if (montant == null) {
            return null;
        }
        
        if ("C".equals(typeMouvement)) {
            return montant; // Positif pour Client
        }
        
        if (!"IB".equals(typeMouvement)) {
            return -montant; // Négatif pour les autres types (sauf IB)
        }
        
        // Cas IB: SI(ET(E7="IB";F7=B7);-L7;L7)
        if ("IB".equals(typeMouvement)) {
            String colF = facture.getColF();
            String colB = facture.getColB(); // Note: FactureVente n'a pas colB, on utilise null
            if (colF != null && colB != null && colF.equals(colB)) {
                return -montant;
            }
            return montant;
        }
        
        return null;
    }
    
    private Double calculerSolde(FactureAchat facture) {
        String typeMouvement = facture.getTypeMouvement();
        Double montant = facture.getTotalTTC();
        
        if (montant == null) {
            return null;
        }
        
        if ("C".equals(typeMouvement)) {
            return montant; // Positif pour Client
        }
        
        if (!"IB".equals(typeMouvement)) {
            return -montant; // Négatif pour les autres types (sauf IB)
        }
        
        // Cas IB: SI(ET(E7="IB";F7=B7);-L7;L7)
        if ("IB".equals(typeMouvement)) {
            String colF = facture.getColF();
            String colB = facture.getColB();
            if (colF != null && colB != null && colF.equals(colB)) {
                return -montant;
            }
            return montant;
        }
        
        return null;
    }
    
    /**
     * Formule: =SI(K7="";"";SI(E7="C";K7;-K7))
     * TOTAL TTC APRES RG (calculé): Signe selon type mouvement
     * K7 = totalTTCApresRG, E7 = typeMouvement
     */
    private Double calculerTotalTTCApresRG_SIGNE(FactureVente facture) {
        Double totalTTCApresRG = calculerTotalTTCApresRG(facture);
        if (totalTTCApresRG == null) {
            return null;
        }
        
        String typeMouvement = facture.getTypeMouvement();
        if ("C".equals(typeMouvement)) {
            return totalTTCApresRG; // Positif pour Client
        }
        return -totalTTCApresRG; // Négatif pour les autres
    }
    
    private Double calculerTotalTTCApresRG_SIGNE(FactureAchat facture) {
        Double totalTTCApresRG = calculerTotalTTCApresRG(facture);
        if (totalTTCApresRG == null) {
            return null;
        }
        
        String typeMouvement = facture.getTypeMouvement();
        if ("C".equals(typeMouvement)) {
            return totalTTCApresRG; // Positif pour Client
        }
        return -totalTTCApresRG; // Négatif pour les autres
    }
    
    /**
     * Calcule le total TTC après remise globale (colonne K).
     * Si totalTTC et tauxRG sont définis: totalTTC * (1 - tauxRG)
     */
    private Double calculerTotalTTCApresRG(FactureVente facture) {
        if (facture.getTotalTTC() == null) {
            return null;
        }
        Double tauxRG = facture.getTauxRG() != null ? facture.getTauxRG() : 0.0;
        return facture.getTotalTTC() * (1 - tauxRG);
    }
    
    private Double calculerTotalTTCApresRG(FactureAchat facture) {
        if (facture.getTotalTTC() == null) {
            return null;
        }
        Double tauxRG = facture.getTauxRG() != null ? facture.getTauxRG() : 0.0;
        return facture.getTotalTTC() * (1 - tauxRG);
    }
    
    /**
     * Formule: =SI(OU(L7="";D7="CCA";E7="IB");"";SI(E7="C";L7;-L7))
     * Total paiement TTC (calculé): Signe selon type mouvement, exclut certains cas
     * L7 = montant (totalTTC), D7 = colD, E7 = typeMouvement
     */
    private Double calculerTotalPaiementTTC(FactureVente facture) {
        Double montant = facture.getTotalTTC();
        if (montant == null) {
            return null;
        }
        
        String colD = facture.getColD();
        String typeMouvement = facture.getTypeMouvement();
        
        // Exclure si D7="CCA" ou E7="IB"
        if ("CCA".equals(colD) || "IB".equals(typeMouvement)) {
            return null;
        }
        
        if ("C".equals(typeMouvement)) {
            return montant; // Positif pour Client
        }
        return -montant; // Négatif pour les autres
    }
    
    private Double calculerTotalPaiementTTC(FactureAchat facture) {
        Double montant = facture.getTotalTTC();
        if (montant == null) {
            return null;
        }
        
        String colD = facture.getColD();
        String typeMouvement = facture.getTypeMouvement();
        
        // Exclure si D7="CCA" ou E7="IB"
        if ("CCA".equals(colD) || "IB".equals(typeMouvement)) {
            return null;
        }
        
        if ("C".equals(typeMouvement)) {
            return montant; // Positif pour Client
        }
        return -montant; // Négatif pour les autres
    }
    
    private Double calculerTotalPaiementTTC(Paiement paiement) {
        Double montant = paiement.getMontant();
        if (montant == null) {
            return null;
        }
        
        String colD = paiement.getColD();
        String typeMouvement = paiement.getTypeMouvement();
        
        // Exclure si D7="CCA" ou E7="IB"
        if ("CCA".equals(colD) || "IB".equals(typeMouvement)) {
            return null;
        }
        
        if ("C".equals(typeMouvement)) {
            return montant; // Positif pour Client
        }
        return -montant; // Négatif pour les autres
    }
    
    /**
     * Formule: =SI(U7="";"";+SI(H7="facture";U7/(1-N7)*N7;""))
     * RG TTC: Remise globale TTC
     * U7 = totalTTCApresRG_SIGNE, H7 = nature, N7 = tauxRG
     */
    private Double calculerRgTTC(FactureVente facture) {
        Double totalTTCApresRG_SIGNE = calculerTotalTTCApresRG_SIGNE(facture);
        if (totalTTCApresRG_SIGNE == null) {
            return null;
        }
        
        String nature = facture.getNature();
        if (!"facture".equals(nature)) {
            return null;
        }
        
        Double tauxRG = facture.getTauxRG() != null ? facture.getTauxRG() : 0.0;
        if (tauxRG == 0.0) {
            return null;
        }
        
        // U7/(1-N7)*N7 = totalTTCApresRG_SIGNE / (1 - tauxRG) * tauxRG
        return totalTTCApresRG_SIGNE / (1 - tauxRG) * tauxRG;
    }
    
    private Double calculerRgTTC(FactureAchat facture) {
        Double totalTTCApresRG_SIGNE = calculerTotalTTCApresRG_SIGNE(facture);
        if (totalTTCApresRG_SIGNE == null) {
            return null;
        }
        
        String nature = facture.getNature();
        if (!"facture".equals(nature)) {
            return null;
        }
        
        Double tauxRG = facture.getTauxRG() != null ? facture.getTauxRG() : 0.0;
        if (tauxRG == 0.0) {
            return null;
        }
        
        // U7/(1-N7)*N7 = totalTTCApresRG_SIGNE / (1 - tauxRG) * tauxRG
        return totalTTCApresRG_SIGNE / (1 - tauxRG) * tauxRG;
    }
    
    /**
     * Formule: =SI(W7="";"";W7/(1+M7))
     * RG HT: Remise globale HT
     * W7 = rgTTC, M7 = tvaRate
     */
    private Double calculerRgHT(FactureVente facture) {
        Double rgTTC = calculerRgTTC(facture);
        if (rgTTC == null) {
            return null;
        }
        
        Double tvaRate = facture.getTvaRate() != null ? facture.getTvaRate() : 0.20;
        return rgTTC / (1 + tvaRate);
    }
    
    private Double calculerRgHT(FactureAchat facture) {
        Double rgTTC = calculerRgTTC(facture);
        if (rgTTC == null) {
            return null;
        }
        
        Double tvaRate = facture.getTvaRate() != null ? facture.getTvaRate() : 0.20;
        return rgTTC / (1 + tvaRate);
    }
    
    /**
     * Formule: =SI(U7="";"";SI(H7="facture";(U7/(1+M7)+W7/(1+M7));""))
     * FACTURE HT YC RG: Facture HT incluant remise globale
     * U7 = totalTTCApresRG_SIGNE, H7 = nature, M7 = tvaRate, W7 = rgTTC
     */
    private Double calculerFactureHT_YC_RG(FactureVente facture) {
        Double totalTTCApresRG_SIGNE = calculerTotalTTCApresRG_SIGNE(facture);
        if (totalTTCApresRG_SIGNE == null) {
            return null;
        }
        
        String nature = facture.getNature();
        if (!"facture".equals(nature)) {
            return null;
        }
        
        Double tvaRate = facture.getTvaRate() != null ? facture.getTvaRate() : 0.20;
        Double rgTTC = calculerRgTTC(facture);
        
        // U7/(1+M7) = totalTTCApresRG_SIGNE / (1 + tvaRate)
        Double htBase = Math.abs(totalTTCApresRG_SIGNE) / (1 + tvaRate);
        
        // W7/(1+M7) = rgTTC / (1 + tvaRate)
        Double htRG = (rgTTC != null) ? Math.abs(rgTTC) / (1 + tvaRate) : 0.0;
        
        return htBase + htRG;
    }
    
    private Double calculerFactureHT_YC_RG(FactureAchat facture) {
        Double totalTTCApresRG_SIGNE = calculerTotalTTCApresRG_SIGNE(facture);
        if (totalTTCApresRG_SIGNE == null) {
            return null;
        }
        
        String nature = facture.getNature();
        if (!"facture".equals(nature)) {
            return null;
        }
        
        Double tvaRate = facture.getTvaRate() != null ? facture.getTvaRate() : 0.20;
        Double rgTTC = calculerRgTTC(facture);
        
        // U7/(1+M7) = totalTTCApresRG_SIGNE / (1 + tvaRate)
        Double htBase = Math.abs(totalTTCApresRG_SIGNE) / (1 + tvaRate);
        
        // W7/(1+M7) = rgTTC / (1 + tvaRate)
        Double htRG = (rgTTC != null) ? Math.abs(rgTTC) / (1 + tvaRate) : 0.0;
        
        return htBase + htRG;
    }
    
    /**
     * Formule: =SI(V7="";"";SI(ET(H7="paiement";OU(E7="C";E7="F";E7="FB";E7="CTP";E7="CTD"));V7/(1+M7);""))
     * HT PAYE: HT payé
     * V7 = totalPaiementTTC, H7 = nature, E7 = typeMouvement, M7 = tvaRate
     */
    private Double calculerHtPaye(FactureVente facture) {
        Double totalPaiementTTC = calculerTotalPaiementTTC(facture);
        if (totalPaiementTTC == null) {
            return null;
        }
        
        String nature = facture.getNature();
        String typeMouvement = facture.getTypeMouvement();
        
        if (!"paiement".equals(nature)) {
            return null;
        }
        
        // Vérifier si typeMouvement est dans la liste: "C", "F", "FB", "CTP", "CTD"
        if (!"C".equals(typeMouvement) && !"F".equals(typeMouvement) && 
            !"FB".equals(typeMouvement) && !"CTP".equals(typeMouvement) && 
            !"CTD".equals(typeMouvement)) {
            return null;
        }
        
        Double tvaRate = facture.getTvaRate() != null ? facture.getTvaRate() : 0.20;
        return Math.abs(totalPaiementTTC) / (1 + tvaRate);
    }
    
    private Double calculerHtPaye(FactureAchat facture) {
        Double totalPaiementTTC = calculerTotalPaiementTTC(facture);
        if (totalPaiementTTC == null) {
            return null;
        }
        
        String nature = facture.getNature();
        String typeMouvement = facture.getTypeMouvement();
        
        if (!"paiement".equals(nature)) {
            return null;
        }
        
        // Vérifier si typeMouvement est dans la liste: "C", "F", "FB", "CTP", "CTD"
        if (!"C".equals(typeMouvement) && !"F".equals(typeMouvement) && 
            !"FB".equals(typeMouvement) && !"CTP".equals(typeMouvement) && 
            !"CTD".equals(typeMouvement)) {
            return null;
        }
        
        Double tvaRate = facture.getTvaRate() != null ? facture.getTvaRate() : 0.20;
        return Math.abs(totalPaiementTTC) / (1 + tvaRate);
    }
    
    private Double calculerHtPaye(Paiement paiement) {
        Double totalPaiementTTC = calculerTotalPaiementTTC(paiement);
        if (totalPaiementTTC == null) {
            return null;
        }
        
        String nature = paiement.getNature();
        String typeMouvement = paiement.getTypeMouvement();
        
        if (!"paiement".equals(nature)) {
            return null;
        }
        
        // Vérifier si typeMouvement est dans la liste: "C", "F", "FB", "CTP", "CTD"
        if (!"C".equals(typeMouvement) && !"F".equals(typeMouvement) && 
            !"FB".equals(typeMouvement) && !"CTP".equals(typeMouvement) && 
            !"CTD".equals(typeMouvement)) {
            return null;
        }
        
        Double tvaRate = paiement.getTvaRate() != null ? paiement.getTvaRate() : 0.20;
        return Math.abs(totalPaiementTTC) / (1 + tvaRate);
    }
    
    /**
     * Formule: =SI(U7="";"";Y7*M7)
     * TVA FACTURE YC RG: TVA facture incluant remise globale
     * U7 = totalTTCApresRG_SIGNE, Y7 = factureHT_YC_RG, M7 = tvaRate
     */
    private Double calculerTvaFactureYcRg(FactureVente facture) {
        Double totalTTCApresRG_SIGNE = calculerTotalTTCApresRG_SIGNE(facture);
        if (totalTTCApresRG_SIGNE == null) {
            return null;
        }
        
        Double factureHT_YC_RG = calculerFactureHT_YC_RG(facture);
        if (factureHT_YC_RG == null) {
            return null;
        }
        
        Double tvaRate = facture.getTvaRate() != null ? facture.getTvaRate() : 0.20;
        return factureHT_YC_RG * tvaRate;
    }
    
    private Double calculerTvaFactureYcRg(FactureAchat facture) {
        Double totalTTCApresRG_SIGNE = calculerTotalTTCApresRG_SIGNE(facture);
        if (totalTTCApresRG_SIGNE == null) {
            return null;
        }
        
        Double factureHT_YC_RG = calculerFactureHT_YC_RG(facture);
        if (factureHT_YC_RG == null) {
            return null;
        }
        
        Double tvaRate = facture.getTvaRate() != null ? facture.getTvaRate() : 0.20;
        return factureHT_YC_RG * tvaRate;
    }
    
    /**
     * Formule: =SI(Z7="";"";V7-Z7)
     * TVA: TVA payée
     * Z7 = htPaye, V7 = totalPaiementTTC
     */
    private Double calculerTvaPaye(FactureVente facture) {
        Double htPaye = calculerHtPaye(facture);
        if (htPaye == null) {
            return null;
        }
        
        Double totalPaiementTTC = calculerTotalPaiementTTC(facture);
        if (totalPaiementTTC == null) {
            return null;
        }
        
        return Math.abs(totalPaiementTTC) - htPaye;
    }
    
    private Double calculerTvaPaye(FactureAchat facture) {
        Double htPaye = calculerHtPaye(facture);
        if (htPaye == null) {
            return null;
        }
        
        Double totalPaiementTTC = calculerTotalPaiementTTC(facture);
        if (totalPaiementTTC == null) {
            return null;
        }
        
        return Math.abs(totalPaiementTTC) - htPaye;
    }
    
    private Double calculerTvaPaye(Paiement paiement) {
        Double htPaye = calculerHtPaye(paiement);
        if (htPaye == null) {
            return null;
        }
        
        Double totalPaiementTTC = calculerTotalPaiementTTC(paiement);
        if (totalPaiementTTC == null) {
            return null;
        }
        
        return Math.abs(totalPaiementTTC) - htPaye;
    }
    
    /**
     * Formule: =SI(OU(D7=$D$2127;A7="CAPITAL");"";SI(OU(E7=$E$2123;E7=$E$2125;E7=$E$2124;E7="CS");"";SI(OU(H7="facture";H7="loy");Y7;SI(OU(E7="F";E7="C";E7="loy");"";-L7/(1+M7)))))
     * Bilan: Bilan HT
     * D7 = colD, A7 = colA, E7 = typeMouvement, H7 = nature, Y7 = factureHT_YC_RG, L7 = montant, M7 = tvaRate
     */
    private Double calculerBilan(FactureVente facture) {
        ParametresCalcul params = parametresCalculService.getParametres();
        
        String colD = facture.getColD();
        String colA = facture.getColA(); // Note: FactureVente n'a pas colA, on utilise null
        
        // Exclure si D7=$D$2127 ou A7="CAPITAL"
        if (params.getCodeDCloture() != null && params.getCodeDCloture().equals(colD)) {
            return null;
        }
        if ("CAPITAL".equals(colA)) {
            return null;
        }
        
        String typeMouvement = facture.getTypeMouvement();
        String nature = facture.getNature();
        
        // Exclure si E7=$E$2123 ou E7=$E$2125 ou E7=$E$2124 ou E7="CS"
        if (params.getCodeEExclu1() != null && params.getCodeEExclu1().equals(typeMouvement)) {
            return null;
        }
        if (params.getCodeEExclu2() != null && params.getCodeEExclu2().equals(typeMouvement)) {
            return null;
        }
        if (params.getCodeEExclu3() != null && params.getCodeEExclu3().equals(typeMouvement)) {
            return null;
        }
        if ("CS".equals(typeMouvement)) {
            return null;
        }
        
        // SI(OU(H7="facture";H7="loy");Y7;...)
        if ("facture".equals(nature) || "loy".equals(nature)) {
            return calculerFactureHT_YC_RG(facture);
        }
        
        // SI(OU(E7="F";E7="C";E7="loy");"";...)
        if ("F".equals(typeMouvement) || "C".equals(typeMouvement) || "loy".equals(typeMouvement)) {
            return null;
        }
        
        // -L7/(1+M7) = -montant / (1 + tvaRate)
        Double montant = facture.getTotalTTC();
        if (montant == null) {
            return null;
        }
        Double tvaRate = facture.getTvaRate() != null ? facture.getTvaRate() : 0.20;
        return -montant / (1 + tvaRate);
    }
    
    private Double calculerBilan(FactureAchat facture) {
        ParametresCalcul params = parametresCalculService.getParametres();
        
        String colD = facture.getColD();
        String colA = facture.getColA();
        
        // Exclure si D7=$D$2127 ou A7="CAPITAL"
        if (params.getCodeDCloture() != null && params.getCodeDCloture().equals(colD)) {
            return null;
        }
        if ("CAPITAL".equals(colA)) {
            return null;
        }
        
        String typeMouvement = facture.getTypeMouvement();
        String nature = facture.getNature();
        
        // Exclure si E7=$E$2123 ou E7=$E$2125 ou E7=$E$2124 ou E7="CS"
        if (params.getCodeEExclu1() != null && params.getCodeEExclu1().equals(typeMouvement)) {
            return null;
        }
        if (params.getCodeEExclu2() != null && params.getCodeEExclu2().equals(typeMouvement)) {
            return null;
        }
        if (params.getCodeEExclu3() != null && params.getCodeEExclu3().equals(typeMouvement)) {
            return null;
        }
        if ("CS".equals(typeMouvement)) {
            return null;
        }
        
        // SI(OU(H7="facture";H7="loy");Y7;...)
        if ("facture".equals(nature) || "loy".equals(nature)) {
            return calculerFactureHT_YC_RG(facture);
        }
        
        // SI(OU(E7="F";E7="C";E7="loy");"";...)
        if ("F".equals(typeMouvement) || "C".equals(typeMouvement) || "loy".equals(typeMouvement)) {
            return null;
        }
        
        // -L7/(1+M7) = -montant / (1 + tvaRate)
        Double montant = facture.getTotalTTC();
        if (montant == null) {
            return null;
        }
        Double tvaRate = facture.getTvaRate() != null ? facture.getTvaRate() : 0.20;
        return -montant / (1 + tvaRate);
    }
}

