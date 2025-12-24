package com.bf4invest.service;

import com.bf4invest.model.DeclarationTVA;
import com.bf4invest.model.EcritureComptable;
import com.bf4invest.model.LigneEcriture;
import com.bf4invest.repository.DeclarationTVARepository;
import com.bf4invest.repository.EcritureComptableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TVAService {

    private final DeclarationTVARepository declarationRepository;
    private final EcritureComptableRepository ecritureRepository;
    private final ComptabiliteService comptabiliteService;

    /**
     * Calcule et génère une déclaration TVA pour un mois/année donné
     */
    public DeclarationTVA calculerDeclarationTVA(Integer mois, Integer annee) {
        // Vérifier si une déclaration existe déjà
        Optional<DeclarationTVA> existing = declarationRepository.findByMoisAndAnnee(mois, annee);
        if (existing.isPresent() && existing.get().getStatut() == DeclarationTVA.StatutDeclaration.DEPOSEE) {
            log.warn("Déclaration TVA déjà déposée pour {}/{}", mois, annee);
            return existing.get();
        }

        LocalDate dateDebut = LocalDate.of(annee, mois, 1);
        LocalDate dateFin = dateDebut.withDayOfMonth(dateDebut.lengthOfMonth());

        // Récupérer toutes les écritures du mois
        List<EcritureComptable> ecritures = ecritureRepository.findByDateEcritureBetween(dateDebut, dateFin);

        // Calculer TVA collectée (compte 4457)
        Double tvaCollectee20 = 0.0;
        Double tvaCollectee14 = 0.0;
        Double tvaCollectee10 = 0.0;
        Double tvaCollectee7 = 0.0;
        Double tvaCollectee0 = 0.0;

        // Calculer TVA déductible (compte 4456)
        Double tvaDeductible20 = 0.0;
        Double tvaDeductible14 = 0.0;
        Double tvaDeductible10 = 0.0;
        Double tvaDeductible7 = 0.0;
        Double tvaDeductible0 = 0.0;

        for (EcritureComptable ecriture : ecritures) {
            if (ecriture.getLignes() == null) continue;

            for (LigneEcriture ligne : ecriture.getLignes()) {
                // TVA collectée (4457) - crédit
                if ("4457".equals(ligne.getCompteCode()) && ligne.getCredit() != null && ligne.getCredit() > 0) {
                    // Déterminer le taux de TVA depuis la facture associée
                    Double taux = getTauxTVAFromEcriture(ecriture);
                    if (taux == null) taux = 0.20; // Par défaut 20%
                    
                    if (taux == 0.20) tvaCollectee20 += ligne.getCredit();
                    else if (taux == 0.14) tvaCollectee14 += ligne.getCredit();
                    else if (taux == 0.10) tvaCollectee10 += ligne.getCredit();
                    else if (taux == 0.07) tvaCollectee7 += ligne.getCredit();
                    else tvaCollectee0 += ligne.getCredit();
                }

                // TVA déductible (4456) - débit
                if ("4456".equals(ligne.getCompteCode()) && ligne.getDebit() != null && ligne.getDebit() > 0) {
                    // Déterminer le taux de TVA depuis la facture associée
                    Double taux = getTauxTVAFromEcriture(ecriture);
                    if (taux == null) taux = 0.20; // Par défaut 20%
                    
                    if (taux == 0.20) tvaDeductible20 += ligne.getDebit();
                    else if (taux == 0.14) tvaDeductible14 += ligne.getDebit();
                    else if (taux == 0.10) tvaDeductible10 += ligne.getDebit();
                    else if (taux == 0.07) tvaDeductible7 += ligne.getDebit();
                    else tvaDeductible0 += ligne.getDebit();
                }
            }
        }

        Double tvaCollecteeTotale = tvaCollectee20 + tvaCollectee14 + tvaCollectee10 + tvaCollectee7 + tvaCollectee0;
        Double tvaDeductibleTotale = tvaDeductible20 + tvaDeductible14 + tvaDeductible10 + tvaDeductible7 + tvaDeductible0;
        Double tvaAPayer = tvaCollecteeTotale - tvaDeductibleTotale;
        Double tvaCredit = tvaAPayer < 0 ? Math.abs(tvaAPayer) : 0.0;
        if (tvaAPayer < 0) tvaAPayer = 0.0;

        String periode = String.format("%02d/%d", mois, annee);

        DeclarationTVA declaration = DeclarationTVA.builder()
                .mois(mois)
                .annee(annee)
                .periode(periode)
                .tvaCollectee20(tvaCollectee20)
                .tvaCollectee14(tvaCollectee14)
                .tvaCollectee10(tvaCollectee10)
                .tvaCollectee7(tvaCollectee7)
                .tvaCollectee0(tvaCollectee0)
                .tvaCollecteeTotale(tvaCollecteeTotale)
                .tvaDeductible20(tvaDeductible20)
                .tvaDeductible14(tvaDeductible14)
                .tvaDeductible10(tvaDeductible10)
                .tvaDeductible7(tvaDeductible7)
                .tvaDeductible0(tvaDeductible0)
                .tvaDeductibleTotale(tvaDeductibleTotale)
                .tvaAPayer(tvaAPayer)
                .tvaCredit(tvaCredit)
                .statut(DeclarationTVA.StatutDeclaration.BROUILLON)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Si une déclaration existe déjà (brouillon), la mettre à jour
        if (existing.isPresent()) {
            declaration.setId(existing.get().getId());
            declaration.setDateDepot(existing.get().getDateDepot());
            declaration.setNotes(existing.get().getNotes());
        }

        return declarationRepository.save(declaration);
    }

    /**
     * Récupère le taux de TVA depuis la facture associée à l'écriture
     */
    private Double getTauxTVAFromEcriture(EcritureComptable ecriture) {
        // TODO: Récupérer le taux TVA depuis la facture via pieceJustificativeId
        // Pour l'instant, on retourne null et on utilise 20% par défaut
        return null;
    }

    /**
     * Valide une déclaration TVA
     */
    public DeclarationTVA validerDeclaration(String id) {
        DeclarationTVA declaration = declarationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Déclaration TVA introuvable"));

        if (declaration.getStatut() == DeclarationTVA.StatutDeclaration.DEPOSEE) {
            throw new IllegalStateException("Déclaration déjà déposée");
        }

        declaration.setStatut(DeclarationTVA.StatutDeclaration.VALIDEE);
        declaration.setUpdatedAt(LocalDateTime.now());
        return declarationRepository.save(declaration);
    }

    /**
     * Marque une déclaration comme déposée
     */
    public DeclarationTVA marquerDeposee(String id, LocalDate dateDepot) {
        DeclarationTVA declaration = declarationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Déclaration TVA introuvable"));

        declaration.setStatut(DeclarationTVA.StatutDeclaration.DEPOSEE);
        declaration.setDateDepot(dateDepot != null ? dateDepot : LocalDate.now());
        declaration.setUpdatedAt(LocalDateTime.now());
        return declarationRepository.save(declaration);
    }

    /**
     * Récupère toutes les déclarations d'une année
     */
    public List<DeclarationTVA> getDeclarationsByAnnee(Integer annee) {
        return declarationRepository.findByAnneeOrderByMoisDesc(annee);
    }

    /**
     * Récupère une déclaration par mois/année
     */
    public Optional<DeclarationTVA> getDeclarationByMoisAnnee(Integer mois, Integer annee) {
        return declarationRepository.findByMoisAndAnnee(mois, annee);
    }
}

