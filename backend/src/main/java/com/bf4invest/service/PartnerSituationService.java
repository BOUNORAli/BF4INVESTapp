package com.bf4invest.service;

import com.bf4invest.dto.MultiPartnerSituationResponse;
import com.bf4invest.dto.PartnerSituationResponse;
import com.bf4invest.model.*;
import com.bf4invest.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerSituationService {
    
    private final ClientRepository clientRepository;
    private final SupplierRepository supplierRepository;
    private final FactureVenteRepository factureVenteRepository;
    private final FactureAchatRepository factureAchatRepository;
    private final PaiementRepository paiementRepository;
    
    public PartnerSituationResponse getClientSituation(String clientId, LocalDate from, LocalDate to) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client non trouvé: " + clientId));
        
        // Récupérer toutes les factures vente du client
        List<FactureVente> factures = factureVenteRepository.findByClientId(clientId);
        
        // Filtrer par période si spécifiée
        if (from != null) {
            factures = factures.stream()
                    .filter(f -> f.getDateFacture() != null && !f.getDateFacture().isBefore(from))
                    .collect(Collectors.toList());
        }
        if (to != null) {
            factures = factures.stream()
                    .filter(f -> f.getDateFacture() != null && !f.getDateFacture().isAfter(to))
                    .collect(Collectors.toList());
        }
        
        // Trier par date de facture (plus récentes en premier)
        factures.sort((a, b) -> {
            if (a.getDateFacture() == null && b.getDateFacture() == null) return 0;
            if (a.getDateFacture() == null) return 1;
            if (b.getDateFacture() == null) return -1;
            return b.getDateFacture().compareTo(a.getDateFacture());
        });
        
        // Construire les détails des factures
        List<PartnerSituationResponse.FactureDetail> factureDetails = new ArrayList<>();
        List<PartnerSituationResponse.PrevisionDetail> previsionDetails = new ArrayList<>();
        
        double totalFactureTTC = 0;
        double totalFactureHT = 0;
        double totalTVA = 0;
        double totalPaye = 0;
        int nombreFacturesPayees = 0;
        int nombreFacturesEnAttente = 0;
        int nombreFacturesEnRetard = 0;
        int nombrePrevisions = 0;
        int nombrePrevisionsRealisees = 0;
        int nombrePrevisionsEnRetard = 0;
        
        LocalDate aujourdhui = LocalDate.now();
        
        for (FactureVente facture : factures) {
            // Récupérer les paiements pour cette facture
            List<Paiement> paiements = paiementRepository.findByFactureVenteId(facture.getId());
            double montantPaye = paiements.stream()
                    .mapToDouble(p -> p.getMontant() != null ? p.getMontant() : 0.0)
                    .sum();
            
            double montantTTC = facture.getTotalTTC() != null ? facture.getTotalTTC() : 0.0;
            // Pour les avoirs, le montant est négatif
            if (Boolean.TRUE.equals(facture.getEstAvoir())) {
                montantTTC = -Math.abs(montantTTC);
                montantPaye = -Math.abs(montantPaye);
            }
            
            double montantRestant = montantTTC - montantPaye;
            
            // Déterminer le statut
            String statut = determinerStatutFacture(facture, montantPaye, montantTTC, aujourdhui);
            
            if ("PAYEE".equals(statut)) {
                nombreFacturesPayees++;
            } else if ("EN_ATTENTE".equals(statut)) {
                nombreFacturesEnAttente++;
            } else if ("EN_RETARD".equals(statut)) {
                nombreFacturesEnRetard++;
            }
            
            // Ajouter aux totaux
            totalFactureTTC += montantTTC;
            totalFactureHT += (facture.getTotalHT() != null ? facture.getTotalHT() : 0.0) * (Boolean.TRUE.equals(facture.getEstAvoir()) ? -1 : 1);
            totalTVA += (facture.getTotalTVA() != null ? facture.getTotalTVA() : 0.0) * (Boolean.TRUE.equals(facture.getEstAvoir()) ? -1 : 1);
            totalPaye += montantPaye;
            
            // Créer le détail de facture
            PartnerSituationResponse.FactureDetail factureDetail = PartnerSituationResponse.FactureDetail.builder()
                    .id(facture.getId())
                    .numeroFacture(facture.getNumeroFactureVente())
                    .dateFacture(facture.getDateFacture())
                    .dateEcheance(facture.getDateEcheance())
                    .montantTTC(montantTTC)
                    .montantHT(facture.getTotalHT() != null ? facture.getTotalHT() : 0.0)
                    .montantTVA(facture.getTotalTVA() != null ? facture.getTotalTVA() : 0.0)
                    .montantPaye(montantPaye)
                    .montantRestant(montantRestant)
                    .statut(statut)
                    .estAvoir(Boolean.TRUE.equals(facture.getEstAvoir()))
                    .numeroFactureOrigine(facture.getNumeroFactureOrigine())
                    .bcReference(facture.getBcReference())
                    .build();
            
            factureDetails.add(factureDetail);
            
            // Traiter les prévisions de paiement
            if (facture.getPrevisionsPaiement() != null) {
                for (PrevisionPaiement prevision : facture.getPrevisionsPaiement()) {
                    nombrePrevisions++;
                    
                    String statutPrevision = prevision.getStatut() != null ? prevision.getStatut() : "PREVU";
                    if ("REALISE".equals(statutPrevision) || "PAYEE".equals(statutPrevision)) {
                        nombrePrevisionsRealisees++;
                    } else if ("EN_RETARD".equals(statutPrevision)) {
                        nombrePrevisionsEnRetard++;
                    }
                    
                    double montantPrevu = prevision.getMontantPrevu() != null ? prevision.getMontantPrevu() : 0.0;
                    double montantPayePrevision = prevision.getMontantPaye() != null ? prevision.getMontantPaye() : 0.0;
                    double montantRestantPrevision = prevision.getMontantRestant() != null ? prevision.getMontantRestant() : (montantPrevu - montantPayePrevision);
                    
                    PartnerSituationResponse.PrevisionDetail previsionDetail = PartnerSituationResponse.PrevisionDetail.builder()
                            .id(prevision.getId())
                            .factureId(facture.getId())
                            .numeroFacture(facture.getNumeroFactureVente())
                            .datePrevue(prevision.getDatePrevue())
                            .montantPrevu(montantPrevu)
                            .montantPaye(montantPayePrevision)
                            .montantRestant(montantRestantPrevision)
                            .statut(statutPrevision)
                            .notes(prevision.getNotes())
                            .build();
                    
                    previsionDetails.add(previsionDetail);
                }
            }
        }
        
        // Trier les prévisions par date
        previsionDetails.sort((a, b) -> {
            if (a.getDatePrevue() == null && b.getDatePrevue() == null) return 0;
            if (a.getDatePrevue() == null) return 1;
            if (b.getDatePrevue() == null) return -1;
            return a.getDatePrevue().compareTo(b.getDatePrevue());
        });
        
        // Construire les informations du partenaire
        PartnerSituationResponse.PartnerInfo partnerInfo = PartnerSituationResponse.PartnerInfo.builder()
                .id(client.getId())
                .nom(client.getNom())
                .ice(client.getIce())
                .reference(client.getReferenceClient())
                .adresse(client.getAdresse())
                .telephone(client.getTelephone())
                .email(client.getEmail())
                .rib(client.getRib())
                .type("CLIENT")
                .build();
        
        // Construire les totaux
        double totalRestant = totalFactureTTC - totalPaye;
        double solde = client.getSoldeClient() != null ? client.getSoldeClient() : totalRestant;
        
        PartnerSituationResponse.Totaux totaux = PartnerSituationResponse.Totaux.builder()
                .totalFactureTTC(totalFactureTTC)
                .totalFactureHT(totalFactureHT)
                .totalTVA(totalTVA)
                .totalPaye(totalPaye)
                .totalRestant(totalRestant)
                .solde(solde)
                .nombreFactures(factures.size())
                .nombreFacturesPayees(nombreFacturesPayees)
                .nombreFacturesEnAttente(nombreFacturesEnAttente)
                .nombreFacturesEnRetard(nombreFacturesEnRetard)
                .nombrePrevisions(nombrePrevisions)
                .nombrePrevisionsRealisees(nombrePrevisionsRealisees)
                .nombrePrevisionsEnRetard(nombrePrevisionsEnRetard)
                .build();
        
        return PartnerSituationResponse.builder()
                .partnerInfo(partnerInfo)
                .dateFrom(from)
                .dateTo(to)
                .factures(factureDetails)
                .previsions(previsionDetails)
                .totaux(totaux)
                .build();
    }
    
    public PartnerSituationResponse getSupplierSituation(String supplierId, LocalDate from, LocalDate to) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new RuntimeException("Fournisseur non trouvé: " + supplierId));
        
        // Récupérer toutes les factures achat du fournisseur
        List<FactureAchat> factures = factureAchatRepository.findByFournisseurId(supplierId);
        
        // Filtrer par période si spécifiée
        if (from != null) {
            factures = factures.stream()
                    .filter(f -> f.getDateFacture() != null && !f.getDateFacture().isBefore(from))
                    .collect(Collectors.toList());
        }
        if (to != null) {
            factures = factures.stream()
                    .filter(f -> f.getDateFacture() != null && !f.getDateFacture().isAfter(to))
                    .collect(Collectors.toList());
        }
        
        // Trier par date de facture (plus récentes en premier)
        factures.sort((a, b) -> {
            if (a.getDateFacture() == null && b.getDateFacture() == null) return 0;
            if (a.getDateFacture() == null) return 1;
            if (b.getDateFacture() == null) return -1;
            return b.getDateFacture().compareTo(a.getDateFacture());
        });
        
        // Construire les détails des factures
        List<PartnerSituationResponse.FactureDetail> factureDetails = new ArrayList<>();
        List<PartnerSituationResponse.PrevisionDetail> previsionDetails = new ArrayList<>();
        
        double totalFactureTTC = 0;
        double totalFactureHT = 0;
        double totalTVA = 0;
        double totalPaye = 0;
        int nombreFacturesPayees = 0;
        int nombreFacturesEnAttente = 0;
        int nombreFacturesEnRetard = 0;
        int nombrePrevisions = 0;
        int nombrePrevisionsRealisees = 0;
        int nombrePrevisionsEnRetard = 0;
        
        LocalDate aujourdhui = LocalDate.now();
        
        for (FactureAchat facture : factures) {
            // Récupérer les paiements pour cette facture
            List<Paiement> paiements = paiementRepository.findByFactureAchatId(facture.getId());
            double montantPaye = paiements.stream()
                    .mapToDouble(p -> p.getMontant() != null ? p.getMontant() : 0.0)
                    .sum();
            
            double montantTTC = facture.getTotalTTC() != null ? facture.getTotalTTC() : 0.0;
            // Pour les avoirs, le montant est négatif
            if (Boolean.TRUE.equals(facture.getEstAvoir())) {
                montantTTC = -Math.abs(montantTTC);
                montantPaye = -Math.abs(montantPaye);
            }
            
            double montantRestant = montantTTC - montantPaye;
            
            // Déterminer le statut
            String statut = determinerStatutFacture(facture, montantPaye, montantTTC, aujourdhui);
            
            if ("PAYEE".equals(statut)) {
                nombreFacturesPayees++;
            } else if ("EN_ATTENTE".equals(statut)) {
                nombreFacturesEnAttente++;
            } else if ("EN_RETARD".equals(statut)) {
                nombreFacturesEnRetard++;
            }
            
            // Ajouter aux totaux
            totalFactureTTC += montantTTC;
            totalFactureHT += (facture.getTotalHT() != null ? facture.getTotalHT() : 0.0) * (Boolean.TRUE.equals(facture.getEstAvoir()) ? -1 : 1);
            totalTVA += (facture.getTotalTVA() != null ? facture.getTotalTVA() : 0.0) * (Boolean.TRUE.equals(facture.getEstAvoir()) ? -1 : 1);
            totalPaye += montantPaye;
            
            // Créer le détail de facture
            PartnerSituationResponse.FactureDetail factureDetail = PartnerSituationResponse.FactureDetail.builder()
                    .id(facture.getId())
                    .numeroFacture(facture.getNumeroFactureAchat())
                    .dateFacture(facture.getDateFacture())
                    .dateEcheance(facture.getDateEcheance())
                    .montantTTC(montantTTC)
                    .montantHT(facture.getTotalHT() != null ? facture.getTotalHT() : 0.0)
                    .montantTVA(facture.getTotalTVA() != null ? facture.getTotalTVA() : 0.0)
                    .montantPaye(montantPaye)
                    .montantRestant(montantRestant)
                    .statut(statut)
                    .estAvoir(Boolean.TRUE.equals(facture.getEstAvoir()))
                    .numeroFactureOrigine(facture.getNumeroFactureOrigine())
                    .bcReference(facture.getBcReference())
                    .build();
            
            factureDetails.add(factureDetail);
            
            // Traiter les prévisions de paiement
            if (facture.getPrevisionsPaiement() != null) {
                for (PrevisionPaiement prevision : facture.getPrevisionsPaiement()) {
                    nombrePrevisions++;
                    
                    String statutPrevision = prevision.getStatut() != null ? prevision.getStatut() : "PREVU";
                    if ("REALISE".equals(statutPrevision) || "PAYEE".equals(statutPrevision)) {
                        nombrePrevisionsRealisees++;
                    } else if ("EN_RETARD".equals(statutPrevision)) {
                        nombrePrevisionsEnRetard++;
                    }
                    
                    double montantPrevu = prevision.getMontantPrevu() != null ? prevision.getMontantPrevu() : 0.0;
                    double montantPayePrevision = prevision.getMontantPaye() != null ? prevision.getMontantPaye() : 0.0;
                    double montantRestantPrevision = prevision.getMontantRestant() != null ? prevision.getMontantRestant() : (montantPrevu - montantPayePrevision);
                    
                    PartnerSituationResponse.PrevisionDetail previsionDetail = PartnerSituationResponse.PrevisionDetail.builder()
                            .id(prevision.getId())
                            .factureId(facture.getId())
                            .numeroFacture(facture.getNumeroFactureAchat())
                            .datePrevue(prevision.getDatePrevue())
                            .montantPrevu(montantPrevu)
                            .montantPaye(montantPayePrevision)
                            .montantRestant(montantRestantPrevision)
                            .statut(statutPrevision)
                            .notes(prevision.getNotes())
                            .build();
                    
                    previsionDetails.add(previsionDetail);
                }
            }
        }
        
        // Trier les prévisions par date
        previsionDetails.sort((a, b) -> {
            if (a.getDatePrevue() == null && b.getDatePrevue() == null) return 0;
            if (a.getDatePrevue() == null) return 1;
            if (b.getDatePrevue() == null) return -1;
            return a.getDatePrevue().compareTo(b.getDatePrevue());
        });
        
        // Construire les informations du partenaire
        PartnerSituationResponse.PartnerInfo partnerInfo = PartnerSituationResponse.PartnerInfo.builder()
                .id(supplier.getId())
                .nom(supplier.getNom())
                .ice(supplier.getIce())
                .reference(supplier.getReferenceFournisseur())
                .adresse(supplier.getAdresse())
                .telephone(supplier.getTelephone())
                .email(supplier.getEmail())
                .rib(supplier.getRib())
                .banque(supplier.getBanque())
                .type("FOURNISSEUR")
                .build();
        
        // Construire les totaux
        double totalRestant = totalFactureTTC - totalPaye;
        double solde = supplier.getSoldeFournisseur() != null ? supplier.getSoldeFournisseur() : totalRestant;
        
        PartnerSituationResponse.Totaux totaux = PartnerSituationResponse.Totaux.builder()
                .totalFactureTTC(totalFactureTTC)
                .totalFactureHT(totalFactureHT)
                .totalTVA(totalTVA)
                .totalPaye(totalPaye)
                .totalRestant(totalRestant)
                .solde(solde)
                .nombreFactures(factures.size())
                .nombreFacturesPayees(nombreFacturesPayees)
                .nombreFacturesEnAttente(nombreFacturesEnAttente)
                .nombreFacturesEnRetard(nombreFacturesEnRetard)
                .nombrePrevisions(nombrePrevisions)
                .nombrePrevisionsRealisees(nombrePrevisionsRealisees)
                .nombrePrevisionsEnRetard(nombrePrevisionsEnRetard)
                .build();
        
        return PartnerSituationResponse.builder()
                .partnerInfo(partnerInfo)
                .dateFrom(from)
                .dateTo(to)
                .factures(factureDetails)
                .previsions(previsionDetails)
                .totaux(totaux)
                .build();
    }
    
    private String determinerStatutFacture(Object facture, double montantPaye, double montantTTC, LocalDate aujourdhui) {
        double tolerance = 0.01; // Tolérance pour les arrondis
        
        // Pour les avoirs, inverser la logique
        boolean estAvoir = false;
        LocalDate dateEcheance = null;
        
        if (facture instanceof FactureVente) {
            estAvoir = Boolean.TRUE.equals(((FactureVente) facture).getEstAvoir());
            dateEcheance = ((FactureVente) facture).getDateEcheance();
        } else if (facture instanceof FactureAchat) {
            estAvoir = Boolean.TRUE.equals(((FactureAchat) facture).getEstAvoir());
            dateEcheance = ((FactureAchat) facture).getDateEcheance();
        }
        
        double montantAbsolu = Math.abs(montantTTC);
        double montantPayeAbsolu = Math.abs(montantPaye);
        
        // Facture payée
        if (montantPayeAbsolu >= (montantAbsolu - tolerance)) {
            return "PAYEE";
        }
        
        // Facture partiellement payée
        if (montantPayeAbsolu > tolerance) {
            // Si l'échéance est passée, c'est en retard
            if (dateEcheance != null && dateEcheance.isBefore(aujourdhui)) {
                return "EN_RETARD";
            }
            return "PARTIELLE";
        }
        
        // Facture non payée
        if (dateEcheance != null && dateEcheance.isBefore(aujourdhui)) {
            return "EN_RETARD";
        }
        
        return "EN_ATTENTE";
    }
    
    public MultiPartnerSituationResponse getMultiClientSituation(List<String> clientIds, LocalDate from, LocalDate to) {
        List<PartnerSituationResponse.PartnerInfo> partnerInfos = new ArrayList<>();
        List<MultiPartnerSituationResponse.FactureDetailWithPartner> facturesConsolidees = new ArrayList<>();
        List<MultiPartnerSituationResponse.PrevisionDetailWithPartner> previsionsConsolidees = new ArrayList<>();
        Map<String, PartnerSituationResponse.Totaux> totauxParPartenaire = new HashMap<>();
        List<PartnerSituationResponse> situationsParPartenaire = new ArrayList<>();
        
        double totalFactureTTCGlobal = 0;
        double totalFactureHTGlobal = 0;
        double totalTVAGlobal = 0;
        double totalPayeGlobal = 0;
        int nombreFacturesGlobal = 0;
        int nombreFacturesPayeesGlobal = 0;
        int nombreFacturesEnAttenteGlobal = 0;
        int nombreFacturesEnRetardGlobal = 0;
        int nombrePrevisionsGlobal = 0;
        int nombrePrevisionsRealiseesGlobal = 0;
        int nombrePrevisionsEnRetardGlobal = 0;
        
        // Traiter chaque client
        for (String clientId : clientIds) {
            try {
                PartnerSituationResponse situation = getClientSituation(clientId, from, to);
                situationsParPartenaire.add(situation);
                
                // Ajouter les infos du partenaire
                partnerInfos.add(situation.getPartnerInfo());
                
                // Ajouter les factures avec info partenaire
                if (situation.getFactures() != null) {
                    for (PartnerSituationResponse.FactureDetail facture : situation.getFactures()) {
                        MultiPartnerSituationResponse.FactureDetailWithPartner factureWithPartner = 
                            MultiPartnerSituationResponse.FactureDetailWithPartner.builder()
                                .partnerId(situation.getPartnerInfo().getId())
                                .partnerNom(situation.getPartnerInfo().getNom())
                                .partnerType("CLIENT")
                                .facture(facture)
                                .build();
                        facturesConsolidees.add(factureWithPartner);
                    }
                }
                
                // Ajouter les prévisions avec info partenaire
                if (situation.getPrevisions() != null) {
                    for (PartnerSituationResponse.PrevisionDetail prevision : situation.getPrevisions()) {
                        MultiPartnerSituationResponse.PrevisionDetailWithPartner previsionWithPartner = 
                            MultiPartnerSituationResponse.PrevisionDetailWithPartner.builder()
                                .partnerId(situation.getPartnerInfo().getId())
                                .partnerNom(situation.getPartnerInfo().getNom())
                                .partnerType("CLIENT")
                                .prevision(prevision)
                                .build();
                        previsionsConsolidees.add(previsionWithPartner);
                    }
                }
                
                // Ajouter les totaux par partenaire
                if (situation.getTotaux() != null) {
                    totauxParPartenaire.put(clientId, situation.getTotaux());
                    
                    // Accumuler les totaux globaux
                    totalFactureTTCGlobal += situation.getTotaux().getTotalFactureTTC() != null ? situation.getTotaux().getTotalFactureTTC() : 0.0;
                    totalFactureHTGlobal += situation.getTotaux().getTotalFactureHT() != null ? situation.getTotaux().getTotalFactureHT() : 0.0;
                    totalTVAGlobal += situation.getTotaux().getTotalTVA() != null ? situation.getTotaux().getTotalTVA() : 0.0;
                    totalPayeGlobal += situation.getTotaux().getTotalPaye() != null ? situation.getTotaux().getTotalPaye() : 0.0;
                    nombreFacturesGlobal += situation.getTotaux().getNombreFactures() != null ? situation.getTotaux().getNombreFactures() : 0;
                    nombreFacturesPayeesGlobal += situation.getTotaux().getNombreFacturesPayees() != null ? situation.getTotaux().getNombreFacturesPayees() : 0;
                    nombreFacturesEnAttenteGlobal += situation.getTotaux().getNombreFacturesEnAttente() != null ? situation.getTotaux().getNombreFacturesEnAttente() : 0;
                    nombreFacturesEnRetardGlobal += situation.getTotaux().getNombreFacturesEnRetard() != null ? situation.getTotaux().getNombreFacturesEnRetard() : 0;
                    nombrePrevisionsGlobal += situation.getTotaux().getNombrePrevisions() != null ? situation.getTotaux().getNombrePrevisions() : 0;
                    nombrePrevisionsRealiseesGlobal += situation.getTotaux().getNombrePrevisionsRealisees() != null ? situation.getTotaux().getNombrePrevisionsRealisees() : 0;
                    nombrePrevisionsEnRetardGlobal += situation.getTotaux().getNombrePrevisionsEnRetard() != null ? situation.getTotaux().getNombrePrevisionsEnRetard() : 0;
                }
            } catch (Exception e) {
                log.warn("Erreur lors de la récupération de la situation pour le client {}: {}", clientId, e.getMessage());
                // Continuer avec les autres clients
            }
        }
        
        // Trier les factures par date (chronologique)
        sortFacturesByDate(facturesConsolidees);
        
        // Trier les prévisions par date (chronologique)
        sortPrevisionsByDate(previsionsConsolidees);
        
        // Calculer le solde global
        double totalRestantGlobal = totalFactureTTCGlobal - totalPayeGlobal;
        double soldeGlobal = situationsParPartenaire.stream()
            .mapToDouble(s -> s.getTotaux() != null && s.getTotaux().getSolde() != null ? s.getTotaux().getSolde() : 0.0)
            .sum();
        
        // Construire les totaux globaux
        MultiPartnerSituationResponse.TotauxGlobaux totauxGlobaux = MultiPartnerSituationResponse.TotauxGlobaux.builder()
            .totalFactureTTC(totalFactureTTCGlobal)
            .totalFactureHT(totalFactureHTGlobal)
            .totalTVA(totalTVAGlobal)
            .totalPaye(totalPayeGlobal)
            .totalRestant(totalRestantGlobal)
            .soldeGlobal(soldeGlobal)
            .nombreFactures(nombreFacturesGlobal)
            .nombreFacturesPayees(nombreFacturesPayeesGlobal)
            .nombreFacturesEnAttente(nombreFacturesEnAttenteGlobal)
            .nombreFacturesEnRetard(nombreFacturesEnRetardGlobal)
            .nombrePrevisions(nombrePrevisionsGlobal)
            .nombrePrevisionsRealisees(nombrePrevisionsRealiseesGlobal)
            .nombrePrevisionsEnRetard(nombrePrevisionsEnRetardGlobal)
            .nombrePartenaires(clientIds.size())
            .build();
        
        return MultiPartnerSituationResponse.builder()
            .partners(partnerInfos)
            .dateFrom(from)
            .dateTo(to)
            .facturesConsolidees(facturesConsolidees)
            .previsionsConsolidees(previsionsConsolidees)
            .totauxGlobaux(totauxGlobaux)
            .totauxParPartenaire(totauxParPartenaire)
            .situationsParPartenaire(situationsParPartenaire)
            .build();
    }
    
    public MultiPartnerSituationResponse getMultiSupplierSituation(List<String> supplierIds, LocalDate from, LocalDate to) {
        List<PartnerSituationResponse.PartnerInfo> partnerInfos = new ArrayList<>();
        List<MultiPartnerSituationResponse.FactureDetailWithPartner> facturesConsolidees = new ArrayList<>();
        List<MultiPartnerSituationResponse.PrevisionDetailWithPartner> previsionsConsolidees = new ArrayList<>();
        Map<String, PartnerSituationResponse.Totaux> totauxParPartenaire = new HashMap<>();
        List<PartnerSituationResponse> situationsParPartenaire = new ArrayList<>();
        
        double totalFactureTTCGlobal = 0;
        double totalFactureHTGlobal = 0;
        double totalTVAGlobal = 0;
        double totalPayeGlobal = 0;
        int nombreFacturesGlobal = 0;
        int nombreFacturesPayeesGlobal = 0;
        int nombreFacturesEnAttenteGlobal = 0;
        int nombreFacturesEnRetardGlobal = 0;
        int nombrePrevisionsGlobal = 0;
        int nombrePrevisionsRealiseesGlobal = 0;
        int nombrePrevisionsEnRetardGlobal = 0;
        
        // Traiter chaque fournisseur
        for (String supplierId : supplierIds) {
            try {
                PartnerSituationResponse situation = getSupplierSituation(supplierId, from, to);
                situationsParPartenaire.add(situation);
                
                // Ajouter les infos du partenaire
                partnerInfos.add(situation.getPartnerInfo());
                
                // Ajouter les factures avec info partenaire
                if (situation.getFactures() != null) {
                    for (PartnerSituationResponse.FactureDetail facture : situation.getFactures()) {
                        MultiPartnerSituationResponse.FactureDetailWithPartner factureWithPartner = 
                            MultiPartnerSituationResponse.FactureDetailWithPartner.builder()
                                .partnerId(situation.getPartnerInfo().getId())
                                .partnerNom(situation.getPartnerInfo().getNom())
                                .partnerType("FOURNISSEUR")
                                .facture(facture)
                                .build();
                        facturesConsolidees.add(factureWithPartner);
                    }
                }
                
                // Ajouter les prévisions avec info partenaire
                if (situation.getPrevisions() != null) {
                    for (PartnerSituationResponse.PrevisionDetail prevision : situation.getPrevisions()) {
                        MultiPartnerSituationResponse.PrevisionDetailWithPartner previsionWithPartner = 
                            MultiPartnerSituationResponse.PrevisionDetailWithPartner.builder()
                                .partnerId(situation.getPartnerInfo().getId())
                                .partnerNom(situation.getPartnerInfo().getNom())
                                .partnerType("FOURNISSEUR")
                                .prevision(prevision)
                                .build();
                        previsionsConsolidees.add(previsionWithPartner);
                    }
                }
                
                // Ajouter les totaux par partenaire
                if (situation.getTotaux() != null) {
                    totauxParPartenaire.put(supplierId, situation.getTotaux());
                    
                    // Accumuler les totaux globaux
                    totalFactureTTCGlobal += situation.getTotaux().getTotalFactureTTC() != null ? situation.getTotaux().getTotalFactureTTC() : 0.0;
                    totalFactureHTGlobal += situation.getTotaux().getTotalFactureHT() != null ? situation.getTotaux().getTotalFactureHT() : 0.0;
                    totalTVAGlobal += situation.getTotaux().getTotalTVA() != null ? situation.getTotaux().getTotalTVA() : 0.0;
                    totalPayeGlobal += situation.getTotaux().getTotalPaye() != null ? situation.getTotaux().getTotalPaye() : 0.0;
                    nombreFacturesGlobal += situation.getTotaux().getNombreFactures() != null ? situation.getTotaux().getNombreFactures() : 0;
                    nombreFacturesPayeesGlobal += situation.getTotaux().getNombreFacturesPayees() != null ? situation.getTotaux().getNombreFacturesPayees() : 0;
                    nombreFacturesEnAttenteGlobal += situation.getTotaux().getNombreFacturesEnAttente() != null ? situation.getTotaux().getNombreFacturesEnAttente() : 0;
                    nombreFacturesEnRetardGlobal += situation.getTotaux().getNombreFacturesEnRetard() != null ? situation.getTotaux().getNombreFacturesEnRetard() : 0;
                    nombrePrevisionsGlobal += situation.getTotaux().getNombrePrevisions() != null ? situation.getTotaux().getNombrePrevisions() : 0;
                    nombrePrevisionsRealiseesGlobal += situation.getTotaux().getNombrePrevisionsRealisees() != null ? situation.getTotaux().getNombrePrevisionsRealisees() : 0;
                    nombrePrevisionsEnRetardGlobal += situation.getTotaux().getNombrePrevisionsEnRetard() != null ? situation.getTotaux().getNombrePrevisionsEnRetard() : 0;
                }
            } catch (Exception e) {
                log.warn("Erreur lors de la récupération de la situation pour le fournisseur {}: {}", supplierId, e.getMessage());
                // Continuer avec les autres fournisseurs
            }
        }
        
        // Trier les factures par date (chronologique)
        sortFacturesByDate(facturesConsolidees);
        
        // Trier les prévisions par date (chronologique)
        sortPrevisionsByDate(previsionsConsolidees);
        
        // Calculer le solde global
        double totalRestantGlobal = totalFactureTTCGlobal - totalPayeGlobal;
        double soldeGlobal = situationsParPartenaire.stream()
            .mapToDouble(s -> s.getTotaux() != null && s.getTotaux().getSolde() != null ? s.getTotaux().getSolde() : 0.0)
            .sum();
        
        // Construire les totaux globaux
        MultiPartnerSituationResponse.TotauxGlobaux totauxGlobaux = MultiPartnerSituationResponse.TotauxGlobaux.builder()
            .totalFactureTTC(totalFactureTTCGlobal)
            .totalFactureHT(totalFactureHTGlobal)
            .totalTVA(totalTVAGlobal)
            .totalPaye(totalPayeGlobal)
            .totalRestant(totalRestantGlobal)
            .soldeGlobal(soldeGlobal)
            .nombreFactures(nombreFacturesGlobal)
            .nombreFacturesPayees(nombreFacturesPayeesGlobal)
            .nombreFacturesEnAttente(nombreFacturesEnAttenteGlobal)
            .nombreFacturesEnRetard(nombreFacturesEnRetardGlobal)
            .nombrePrevisions(nombrePrevisionsGlobal)
            .nombrePrevisionsRealisees(nombrePrevisionsRealiseesGlobal)
            .nombrePrevisionsEnRetard(nombrePrevisionsEnRetardGlobal)
            .nombrePartenaires(supplierIds.size())
            .build();
        
        return MultiPartnerSituationResponse.builder()
            .partners(partnerInfos)
            .dateFrom(from)
            .dateTo(to)
            .facturesConsolidees(facturesConsolidees)
            .previsionsConsolidees(previsionsConsolidees)
            .totauxGlobaux(totauxGlobaux)
            .totauxParPartenaire(totauxParPartenaire)
            .situationsParPartenaire(situationsParPartenaire)
            .build();
    }
    
    private void sortFacturesByDate(List<MultiPartnerSituationResponse.FactureDetailWithPartner> factures) {
        factures.sort((a, b) -> {
            LocalDate dateA = a.getFacture() != null ? a.getFacture().getDateFacture() : null;
            LocalDate dateB = b.getFacture() != null ? b.getFacture().getDateFacture() : null;
            
            if (dateA == null && dateB == null) return 0;
            if (dateA == null) return 1;
            if (dateB == null) return -1;
            return dateA.compareTo(dateB); // Tri chronologique (plus anciennes en premier)
        });
    }
    
    private void sortPrevisionsByDate(List<MultiPartnerSituationResponse.PrevisionDetailWithPartner> previsions) {
        previsions.sort((a, b) -> {
            LocalDate dateA = a.getPrevision() != null ? a.getPrevision().getDatePrevue() : null;
            LocalDate dateB = b.getPrevision() != null ? b.getPrevision().getDatePrevue() : null;
            
            if (dateA == null && dateB == null) return 0;
            if (dateA == null) return 1;
            if (dateB == null) return -1;
            return dateA.compareTo(dateB); // Tri chronologique (plus anciennes en premier)
        });
    }
}

