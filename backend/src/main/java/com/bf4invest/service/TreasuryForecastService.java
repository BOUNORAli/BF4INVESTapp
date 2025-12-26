package com.bf4invest.service;

import com.bf4invest.dto.DashboardKpiResponse;
import com.bf4invest.dto.EcheanceDetail;
import com.bf4invest.dto.PrevisionJournaliere;
import com.bf4invest.model.Client;
import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.FactureVente;
import com.bf4invest.model.PrevisionPaiement;
import com.bf4invest.model.Supplier;
import com.bf4invest.repository.ClientRepository;
import com.bf4invest.repository.FactureAchatRepository;
import com.bf4invest.repository.FactureVenteRepository;
import com.bf4invest.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TreasuryForecastService {
    
    private final SoldeService soldeService;
    private final PrevisionTresorerieService previsionTresorerieService;
    private final FactureVenteRepository factureVenteRepository;
    private final FactureAchatRepository factureAchatRepository;
    private final ClientRepository clientRepository;
    private final SupplierRepository supplierRepository;
    
    public DashboardKpiResponse.TreasuryForecast generateForecast(LocalDate from, LocalDate to) {
        // Récupérer le solde actuel
        Double soldeActuel = soldeService.getSoldeGlobalActuel();
        
        // Si pas de dates, utiliser les 3 prochains mois
        if (from == null) {
            from = LocalDate.now();
        }
        if (to == null) {
            to = from.plusMonths(3);
        }
        
        // Récupérer les prévisions via le service existant
        var previsionResponse = previsionTresorerieService.getPrevisionTresorerie(from, to);
        
        // Construire les prévisions jour par jour pour les 3 mois
        List<DashboardKpiResponse.ForecastData> previsions3Mois = new ArrayList<>();
        double soldeCourant = soldeActuel;
        
        // Grouper les échéances par date
        Map<LocalDate, List<EcheanceDetail>> echeancesParDate = previsionResponse.getEcheances().stream()
                .collect(Collectors.groupingBy(EcheanceDetail::getDate));
        
        // Générer les prévisions pour chaque jour (ou semaine pour simplifier)
        LocalDate currentDate = from;
        while (!currentDate.isAfter(to)) {
            List<EcheanceDetail> echeancesDuJour = echeancesParDate.getOrDefault(currentDate, Collections.emptyList());
            
            double encaissementsPrevu = echeancesDuJour.stream()
                    .filter(e -> "VENTE".equals(e.getType()))
                    .mapToDouble(EcheanceDetail::getMontant)
                    .sum();
            
            double decaissementsPrevu = echeancesDuJour.stream()
                    .filter(e -> "ACHAT".equals(e.getType()) || "CHARGE".equals(e.getType()))
                    .mapToDouble(EcheanceDetail::getMontant)
                    .sum();
            
            soldeCourant = soldeCourant + encaissementsPrevu - decaissementsPrevu;
            
            previsions3Mois.add(DashboardKpiResponse.ForecastData.builder()
                    .date(currentDate)
                    .soldePrevu(soldeCourant)
                    .encaissementsPrevu(encaissementsPrevu)
                    .decaissementsPrevu(decaissementsPrevu)
                    .build());
            
            currentDate = currentDate.plusDays(1);
        }
        
        // Échéances clients
        List<DashboardKpiResponse.EcheanceDetail> echeancesClients = previsionResponse.getEcheances().stream()
                .filter(e -> "VENTE".equals(e.getType()))
                .map(e -> DashboardKpiResponse.EcheanceDetail.builder()
                        .date(e.getDate())
                        .type("CLIENT")
                        .partenaire(e.getPartenaire())
                        .montant(e.getMontant())
                        .statut(e.getStatut())
                        .build())
                .collect(Collectors.toList());
        
        // Échéances fournisseurs
        List<DashboardKpiResponse.EcheanceDetail> echeancesFournisseurs = previsionResponse.getEcheances().stream()
                .filter(e -> "ACHAT".equals(e.getType()) || "CHARGE".equals(e.getType()))
                .map(e -> DashboardKpiResponse.EcheanceDetail.builder()
                        .date(e.getDate())
                        .type("FOURNISSEUR")
                        .partenaire(e.getPartenaire())
                        .montant(e.getMontant())
                        .statut(e.getStatut())
                        .build())
                .collect(Collectors.toList());
        
        // Alertes
        List<String> alertes = new ArrayList<>();
        
        // Vérifier si le solde devient négatif
        for (DashboardKpiResponse.ForecastData prevision : previsions3Mois) {
            if (prevision.getSoldePrevu() < 0) {
                alertes.add("Solde négatif prévu le " + prevision.getDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                break; // Une seule alerte suffit
            }
        }
        
        // Vérifier les échéances en retard
        long echeancesEnRetard = previsionResponse.getEcheances().stream()
                .filter(e -> "EN_RETARD".equals(e.getStatut()))
                .count();
        
        if (echeancesEnRetard > 0) {
            alertes.add(echeancesEnRetard + " échéance(s) en retard");
        }
        
        return DashboardKpiResponse.TreasuryForecast.builder()
                .soldeActuel(soldeActuel)
                .previsions3Mois(previsions3Mois)
                .echeancesClients(echeancesClients)
                .echeancesFournisseurs(echeancesFournisseurs)
                .alertes(alertes)
                .build();
    }
}

