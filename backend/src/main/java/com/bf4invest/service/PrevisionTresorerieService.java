package com.bf4invest.service;

import com.bf4invest.dto.EcheanceDetail;
import com.bf4invest.dto.PrevisionJournaliere;
import com.bf4invest.dto.PrevisionTresorerieResponse;
import com.bf4invest.model.Charge;
import com.bf4invest.model.Client;
import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.FactureVente;
import com.bf4invest.model.PrevisionPaiement;
import com.bf4invest.repository.ChargeRepository;
import com.bf4invest.repository.ClientRepository;
import com.bf4invest.repository.FactureAchatRepository;
import com.bf4invest.repository.FactureVenteRepository;
import com.bf4invest.repository.SupplierRepository;
import com.bf4invest.util.NumberUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrevisionTresorerieService {
    
    private final FactureVenteRepository factureVenteRepository;
    private final FactureAchatRepository factureAchatRepository;
    private final ClientRepository clientRepository;
    private final SupplierRepository supplierRepository;
    private final SoldeService soldeService;
    private final ChargeRepository chargeRepository;
    
    public PrevisionTresorerieResponse getPrevisionTresorerie(LocalDate from, LocalDate to) {
        // Récupérer le solde actuel
        Double soldeActuel = soldeService.getSoldeGlobalActuel();
        
        // Récupérer toutes les factures avec leurs prévisions
        List<FactureVente> facturesVente = factureVenteRepository.findAll();
        List<FactureAchat> facturesAchat = factureAchatRepository.findAll();
        
        // Construire la liste des échéances
        List<EcheanceDetail> echeances = new ArrayList<>();
        
        // Ajouter les prévisions des factures vente (entrées)
        for (FactureVente fv : facturesVente) {
            if (fv.getPrevisionsPaiement() != null) {
                Client client = fv.getClientId() != null ? 
                    clientRepository.findById(fv.getClientId()).orElse(null) : null;
                String clientNom = client != null ? client.getNom() : "Client inconnu";
                
                for (PrevisionPaiement prev : fv.getPrevisionsPaiement()) {
                    if (prev.getDatePrevue() != null && 
                        !prev.getDatePrevue().isBefore(from) && 
                        !prev.getDatePrevue().isAfter(to)) {
                        
                        String statut = determinerStatut(prev);
                        
                        echeances.add(EcheanceDetail.builder()
                            .date(prev.getDatePrevue())
                            .type("VENTE")
                            .numeroFacture(fv.getNumeroFactureVente())
                            .partenaire(clientNom)
                            .montant(NumberUtils.roundTo2Decimals(prev.getMontantPrevu() != null ? prev.getMontantPrevu() : 0.0))
                            .statut(statut)
                            .factureId(fv.getId())
                            .build());
                    }
                }
            }
        }
        
        // Ajouter les prévisions des factures achat (sorties)
        for (FactureAchat fa : facturesAchat) {
            if (fa.getPrevisionsPaiement() != null) {
                com.bf4invest.model.Supplier supplier = fa.getFournisseurId() != null ? 
                    supplierRepository.findById(fa.getFournisseurId()).orElse(null) : null;
                String supplierNom = supplier != null ? supplier.getNom() : "Fournisseur inconnu";
                
                for (PrevisionPaiement prev : fa.getPrevisionsPaiement()) {
                    if (prev.getDatePrevue() != null && 
                        !prev.getDatePrevue().isBefore(from) && 
                        !prev.getDatePrevue().isAfter(to)) {
                        
                        String statut = determinerStatut(prev);
                        
                        echeances.add(EcheanceDetail.builder()
                            .date(prev.getDatePrevue())
                            .type("ACHAT")
                            .numeroFacture(fa.getNumeroFactureAchat())
                            .partenaire(supplierNom)
                            .montant(prev.getMontantPrevu())
                            .statut(statut)
                            .factureId(fa.getId())
                            .build());
                    }
                }
            }
        }

        // Ajouter les charges (sorties) - uniquement celles PREVUE
        List<Charge> charges = chargeRepository.findAll();
        for (Charge charge : charges) {
            if (charge == null) continue;
            if (charge.getDateEcheance() == null) continue;
            if (!"PREVUE".equalsIgnoreCase(charge.getStatut())) continue;

            if (!charge.getDateEcheance().isBefore(from) && !charge.getDateEcheance().isAfter(to)) {
                String statut = determinerStatutCharge(charge);
                echeances.add(EcheanceDetail.builder()
                        .date(charge.getDateEcheance())
                        .type("CHARGE")
                        .numeroFacture(charge.getLibelle() != null ? charge.getLibelle() : "Charge")
                        .partenaire(charge.getCategorie() != null ? charge.getCategorie() : "Charge")
                        .montant(NumberUtils.roundTo2Decimals(charge.getMontant() != null ? charge.getMontant() : 0.0))
                        .statut(statut)
                        .factureId(charge.getId())
                        .build());
            }
        }
        
        // Trier les échéances par date
        echeances.sort(Comparator.comparing(EcheanceDetail::getDate));
        
        // Calculer les prévisions journalières
        List<PrevisionJournaliere> previsions = calculerPrevisionsJournalieres(
            from, to, soldeActuel, echeances);
        
        return PrevisionTresorerieResponse.builder()
            .soldeActuel(NumberUtils.roundTo2Decimals(soldeActuel))
            .previsions(previsions)
            .echeances(echeances)
            .build();
    }
    
    private String determinerStatut(PrevisionPaiement prev) {
        if (prev.getStatut() != null) {
            return prev.getStatut();
        }
        
        LocalDate aujourdhui = LocalDate.now();
        if (prev.getDatePrevue().isBefore(aujourdhui)) {
            return "EN_RETARD";
        } else {
            return "PREVU";
        }
    }

    private String determinerStatutCharge(Charge charge) {
        LocalDate aujourdhui = LocalDate.now();
        if (charge.getDateEcheance() != null && charge.getDateEcheance().isBefore(aujourdhui)) {
            return "EN_RETARD";
        }
        return "PREVU";
    }
    
    private List<PrevisionJournaliere> calculerPrevisionsJournalieres(
            LocalDate from, LocalDate to, Double soldeInitial, List<EcheanceDetail> echeances) {
        
        List<PrevisionJournaliere> previsions = new ArrayList<>();
        
        // Grouper les échéances par date
        Map<LocalDate, List<EcheanceDetail>> echeancesParDate = echeances.stream()
            .collect(Collectors.groupingBy(EcheanceDetail::getDate));
        
        // Calculer jour par jour
        double soldeCourant = soldeInitial != null ? soldeInitial : 0.0;
        LocalDate dateCourante = from;
        
        while (!dateCourante.isAfter(to)) {
            List<EcheanceDetail> echeancesDuJour = echeancesParDate.getOrDefault(dateCourante, new ArrayList<>());
            
            double entrees = NumberUtils.roundTo2Decimals(echeancesDuJour.stream()
                .filter(e -> "VENTE".equals(e.getType()))
                .mapToDouble(e -> e.getMontant() != null ? e.getMontant() : 0.0)
                .sum());
            
            double sorties = NumberUtils.roundTo2Decimals(echeancesDuJour.stream()
                .filter(e -> "ACHAT".equals(e.getType()) || "CHARGE".equals(e.getType()))
                .mapToDouble(e -> e.getMontant() != null ? e.getMontant() : 0.0)
                .sum());
            
            soldeCourant = NumberUtils.roundTo2Decimals(soldeCourant + entrees - sorties);
            
            previsions.add(PrevisionJournaliere.builder()
                .date(dateCourante)
                .entreesPrevisionnelles(entrees)
                .sortiesPrevisionnelles(sorties)
                .soldePrevu(soldeCourant)
                .build());
            
            dateCourante = dateCourante.plusDays(1);
        }
        
        return previsions;
    }
}

