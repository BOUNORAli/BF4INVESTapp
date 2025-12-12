package com.bf4invest.scheduler;

import com.bf4invest.model.FactureAchat;
import com.bf4invest.repository.FactureAchatRepository;
import com.bf4invest.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {
    
    private final FactureAchatRepository factureAchatRepository;
    private final NotificationService notificationService;
    
    // Exécuté tous les jours à 02:00
    @Scheduled(cron = "0 0 2 * * ?")
    public void checkOverduePurchaseInvoices() {
        log.info("Démarrage de la vérification des factures achat en retard...");
        
        LocalDate today = LocalDate.now();
        List<FactureAchat> overdueInvoices = factureAchatRepository
                .findByDateEcheanceLessThanEqual(today);
        
        for (FactureAchat facture : overdueInvoices) {
            if (!"regle".equals(facture.getEtatPaiement())) {
                // Vérifier si plus de 60 jours depuis la date de facture
                LocalDate dateFacture = facture.getDateFacture();
                if (dateFacture != null) {
                    LocalDate dateLimite = dateFacture.plusDays(60);
                    
                    if (today.isAfter(dateLimite) || today.isEqual(dateLimite)) {
                        // Alerte critique - TVA en danger
                        notificationService.createNotification(
                                "FA_NON_REGLEE",
                                facture.getId(),
                                "critique",
                                "Alerte TVA - Facture en retard",
                                String.format("La facture %s dépasse 60 jours sans règlement. Alerte TVA déclenchée.",
                                        facture.getNumeroFactureAchat())
                        );
                        log.warn("Alerte critique pour facture {} - dépassement 60 jours", facture.getNumeroFactureAchat());
                    } else if (today.isAfter(facture.getDateEcheance())) {
                        // Alerte normale - échéance dépassée
                        notificationService.createNotification(
                                "FA_NON_REGLEE",
                                facture.getId(),
                                "warning",
                                "Facture achat en retard",
                                String.format("La facture %s a dépassé sa date d'échéance (%s).",
                                        facture.getNumeroFactureAchat(),
                                        facture.getDateEcheance())
                        );
                        log.info("Alerte pour facture {} - échéance dépassée", facture.getNumeroFactureAchat());
                    }
                }
            }
        }
        
        log.info("Vérification terminée. {} factures vérifiées.", overdueInvoices.size());
    }
}




