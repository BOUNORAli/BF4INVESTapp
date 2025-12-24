package com.bf4invest.service;

import com.bf4invest.model.ExerciceComptable;
import com.bf4invest.repository.ExerciceComptableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ISService {

    private final ComptabiliteService comptabiliteService;
    private final ExerciceComptableRepository exerciceRepository;

    /**
     * Calcule l'IS (Impôt sur les Sociétés) pour une période donnée
     * Barème progressif marocain:
     * - 10% jusqu'à 300 000 MAD
     * - 20% de 300 001 à 1 000 000 MAD
     * - 31% au-delà (ou 28% selon secteur)
     */
    public Map<String, Object> calculerIS(LocalDate dateDebut, LocalDate dateFin, String exerciceId) {
        // Récupérer le CPC pour obtenir le résultat net
        Map<String, Object> cpc = comptabiliteService.getCPC(dateDebut, dateFin, exerciceId);
        Double resultatNet = (Double) cpc.get("resultatNet");
        if (resultatNet == null) resultatNet = 0.0;

        // Résultat fiscal = Résultat comptable + réintégrations - déductions
        // Pour simplifier, on prend le résultat net tel quel (à affiner selon besoins)
        Double resultatFiscal = resultatNet;

        // Calculer le CA pour la cotisation minimale
        Double ca = (Double) cpc.get("produitsExploitation");
        if (ca == null) ca = 0.0;

        // Cotisation minimale = 0.5% du CA, minimum 3000 MAD
        Double cotisationMinimale = Math.max(ca * 0.005, 3000.0);

        // Calcul IS selon barème progressif
        Double isCalcule = 0.0;
        if (resultatFiscal > 0) {
            if (resultatFiscal <= 300000) {
                isCalcule = resultatFiscal * 0.10;
            } else if (resultatFiscal <= 1000000) {
                isCalcule = 300000 * 0.10 + (resultatFiscal - 300000) * 0.20;
            } else {
                isCalcule = 300000 * 0.10 + 700000 * 0.20 + (resultatFiscal - 1000000) * 0.31;
            }
        }

        // L'IS à payer est le maximum entre IS calculé et cotisation minimale
        Double isAPayer = Math.max(isCalcule, cotisationMinimale);

        Map<String, Object> result = new HashMap<>();
        result.put("dateDebut", dateDebut);
        result.put("dateFin", dateFin);
        result.put("resultatNet", resultatNet);
        result.put("resultatFiscal", resultatFiscal);
        result.put("ca", ca);
        result.put("isCalcule", isCalcule);
        result.put("cotisationMinimale", cotisationMinimale);
        result.put("isAPayer", isAPayer);

        return result;
    }

    /**
     * Calcule les acomptes provisionnels IS pour l'année
     * 4 acomptes: 31/03, 30/06, 30/09, 31/12
     * Chaque acompte = 25% de l'IS de l'année précédente
     */
    public List<Map<String, Object>> calculerAcomptes(Integer annee) {
        // Récupérer l'IS de l'année précédente
        Integer anneePrecedente = annee - 1;
        LocalDate debutPrecedent = LocalDate.of(anneePrecedente, 1, 1);
        LocalDate finPrecedent = LocalDate.of(anneePrecedente, 12, 31);
        
        Map<String, Object> isPrecedent = calculerIS(debutPrecedent, finPrecedent, null);
        Double isAnneePrecedente = (Double) isPrecedent.get("isAPayer");
        if (isAnneePrecedente == null) isAnneePrecedente = 0.0;

        Double montantAcompte = isAnneePrecedente * 0.25;

        List<Map<String, Object>> acomptes = new ArrayList<>();
        
        // Acompte 1: 31/03
        Map<String, Object> ac1 = new HashMap<>();
        ac1.put("numero", 1);
        ac1.put("dateEcheance", LocalDate.of(annee, 3, 31));
        ac1.put("montant", montantAcompte);
        ac1.put("statut", "EN_ATTENTE");
        acomptes.add(ac1);

        // Acompte 2: 30/06
        Map<String, Object> ac2 = new HashMap<>();
        ac2.put("numero", 2);
        ac2.put("dateEcheance", LocalDate.of(annee, 6, 30));
        ac2.put("montant", montantAcompte);
        ac2.put("statut", "EN_ATTENTE");
        acomptes.add(ac2);

        // Acompte 3: 30/09
        Map<String, Object> ac3 = new HashMap<>();
        ac3.put("numero", 3);
        ac3.put("dateEcheance", LocalDate.of(annee, 9, 30));
        ac3.put("montant", montantAcompte);
        ac3.put("statut", "EN_ATTENTE");
        acomptes.add(ac3);

        // Acompte 4: 31/12
        Map<String, Object> ac4 = new HashMap<>();
        ac4.put("numero", 4);
        ac4.put("dateEcheance", LocalDate.of(annee, 12, 31));
        ac4.put("montant", montantAcompte);
        ac4.put("statut", "EN_ATTENTE");
        acomptes.add(ac4);

        return acomptes;
    }
}

