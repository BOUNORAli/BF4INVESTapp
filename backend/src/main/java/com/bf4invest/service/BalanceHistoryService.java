package com.bf4invest.service;

import com.bf4invest.dto.DashboardKpiResponse;
import com.bf4invest.model.*;
import com.bf4invest.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BalanceHistoryService {
    
    private final HistoriqueSoldeRepository historiqueSoldeRepository;
    private final SoldeService soldeService;
    private final ClientRepository clientRepository;
    private final SupplierRepository supplierRepository;
    
    public DashboardKpiResponse.BalanceHistory getBalanceHistory(LocalDate from, LocalDate to) {
        // Récupérer l'historique des mouvements
        List<HistoriqueSolde> historique = historiqueSoldeRepository.findAllByOrderByDateDesc();
        
        // Filtrer par période si nécessaire
        if (from != null || to != null) {
            LocalDateTime fromDateTime = from != null ? from.atStartOfDay() : null;
            LocalDateTime toDateTime = to != null ? to.atTime(23, 59, 59) : null;
            
            historique = historique.stream()
                    .filter(h -> {
                        if (h.getDate() == null) return false;
                        if (fromDateTime != null && h.getDate().isBefore(fromDateTime)) return false;
                        if (toDateTime != null && h.getDate().isAfter(toDateTime)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }
        
        // Convertir en BalanceMovement
        List<DashboardKpiResponse.BalanceMovement> mouvements = historique.stream()
                .map(h -> DashboardKpiResponse.BalanceMovement.builder()
                        .date(h.getDate() != null ? h.getDate().toLocalDate() : LocalDate.now())
                        .type(h.getType())
                        .reference(h.getReferenceNumero() != null ? h.getReferenceNumero() : "")
                        .partenaire(h.getPartenaireNom() != null ? h.getPartenaireNom() : "")
                        .montant(h.getMontant() != null ? h.getMontant() : 0.0)
                        .soldeAvant(h.getSoldeGlobalAvant() != null ? h.getSoldeGlobalAvant() : 0.0)
                        .soldeApres(h.getSoldeGlobalApres() != null ? h.getSoldeGlobalApres() : 0.0)
                        .build())
                .collect(Collectors.toList());
        
        // Solde initial (premier mouvement ou solde actuel - somme des mouvements)
        double soldeInitial = 0.0;
        if (!mouvements.isEmpty()) {
            soldeInitial = mouvements.get(mouvements.size() - 1).getSoldeAvant();
        } else {
            // Si pas de mouvements, utiliser le solde actuel
            soldeInitial = soldeService.getSoldeGlobalActuel();
        }
        
        // Solde actuel
        double soldeActuel = soldeService.getSoldeGlobalActuel();
        
        // Solde par partenaire
        Map<String, PartnerBalance> soldeParPartenaireMap = new HashMap<>();
        
        for (HistoriqueSolde h : historique) {
            if (h.getPartenaireId() != null && h.getPartenaireType() != null) {
                String key = h.getPartenaireId() + "_" + h.getPartenaireType();
                PartnerBalance balance = soldeParPartenaireMap.computeIfAbsent(key, k -> new PartnerBalance());
                balance.partenaireId = h.getPartenaireId();
                balance.partenaireType = h.getPartenaireType();
                balance.partenaireNom = h.getPartenaireNom();
                balance.solde = h.getSoldePartenaireApres() != null ? h.getSoldePartenaireApres() : 0.0;
            }
        }
        
        // Récupérer les soldes depuis les entités Client et Supplier pour ceux qui n'ont pas d'historique
        List<Client> clients = clientRepository.findAll();
        for (Client c : clients) {
            String key = c.getId() + "_CLIENT";
            if (!soldeParPartenaireMap.containsKey(key)) {
                PartnerBalance balance = new PartnerBalance();
                balance.partenaireId = c.getId();
                balance.partenaireType = "CLIENT";
                balance.partenaireNom = c.getNom();
                balance.solde = c.getSoldeClient() != null ? c.getSoldeClient() : 0.0;
                soldeParPartenaireMap.put(key, balance);
            }
        }
        
        List<Supplier> suppliers = supplierRepository.findAll();
        for (Supplier s : suppliers) {
            String key = s.getId() + "_FOURNISSEUR";
            if (!soldeParPartenaireMap.containsKey(key)) {
                PartnerBalance balance = new PartnerBalance();
                balance.partenaireId = s.getId();
                balance.partenaireType = "FOURNISSEUR";
                balance.partenaireNom = s.getNom();
                balance.solde = s.getSoldeFournisseur() != null ? s.getSoldeFournisseur() : 0.0;
                soldeParPartenaireMap.put(key, balance);
            }
        }
        
        List<DashboardKpiResponse.BalanceByPartner> soldeParPartenaire = soldeParPartenaireMap.values().stream()
                .filter(b -> Math.abs(b.solde) > 0.01) // Filtrer les soldes proches de zéro
                .sorted((b1, b2) -> Double.compare(Math.abs(b2.solde), Math.abs(b1.solde)))
                .map(b -> DashboardKpiResponse.BalanceByPartner.builder()
                        .partenaireId(b.partenaireId)
                        .partenaireNom(b.partenaireNom != null ? b.partenaireNom : "")
                        .partenaireType(b.partenaireType)
                        .solde(b.solde)
                        .build())
                .collect(Collectors.toList());
        
        return DashboardKpiResponse.BalanceHistory.builder()
                .mouvements(mouvements)
                .soldeInitial(soldeInitial)
                .soldeActuel(soldeActuel)
                .soldeParPartenaire(soldeParPartenaire)
                .build();
    }
    
    private static class PartnerBalance {
        String partenaireId;
        String partenaireType;
        String partenaireNom;
        double solde;
    }
}

