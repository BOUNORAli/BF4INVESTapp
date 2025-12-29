package com.bf4invest.service;

import com.bf4invest.model.Client;
import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.FactureVente;
import com.bf4invest.model.HistoriqueSolde;
import com.bf4invest.model.Paiement;
import com.bf4invest.model.SoldeGlobal;
import com.bf4invest.model.Supplier;
import com.bf4invest.repository.ClientRepository;
import com.bf4invest.repository.HistoriqueSoldeRepository;
import com.bf4invest.repository.SoldeGlobalRepository;
import com.bf4invest.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SoldeService {
    
    private final SoldeGlobalRepository soldeGlobalRepository;
    private final HistoriqueSoldeRepository historiqueSoldeRepository;
    private final ClientRepository clientRepository;
    private final SupplierRepository supplierRepository;
    
    /**
     * Initialise le solde de départ
     */
    @Transactional
    public SoldeGlobal initialiserSoldeDepart(Double montant, LocalDate dateDebut) {
        List<SoldeGlobal> existing = soldeGlobalRepository.findAll();
        SoldeGlobal soldeGlobal;
        
        if (existing.isEmpty()) {
            // Créer un nouveau solde global
            soldeGlobal = SoldeGlobal.builder()
                    .soldeInitial(montant)
                    .soldeActuel(montant)
                    .dateDebut(dateDebut != null ? dateDebut : LocalDate.now())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        } else {
            // Mettre à jour le solde existant
            soldeGlobal = existing.get(0);
            soldeGlobal.setSoldeInitial(montant);
            soldeGlobal.setSoldeActuel(montant);
            if (dateDebut != null) {
                soldeGlobal.setDateDebut(dateDebut);
            }
            soldeGlobal.setUpdatedAt(LocalDateTime.now());
        }
        
        return soldeGlobalRepository.save(soldeGlobal);
    }
    
    /**
     * Récupère le solde global actuel
     */
    public Double getSoldeGlobalActuel() {
        return soldeGlobalRepository.findAll().stream()
                .findFirst()
                .map(SoldeGlobal::getSoldeActuel)
                .orElse(0.0);
    }
    
    /**
     * Récupère le solde d'un partenaire (client ou fournisseur)
     */
    public Double getSoldePartenaire(String id, String type) {
        if ("CLIENT".equals(type)) {
            return clientRepository.findById(id)
                    .map(client -> client.getSoldeClient() != null ? client.getSoldeClient() : 0.0)
                    .orElse(0.0);
        } else if ("FOURNISSEUR".equals(type)) {
            return supplierRepository.findById(id)
                    .map(supplier -> supplier.getSoldeFournisseur() != null ? supplier.getSoldeFournisseur() : 0.0)
                    .orElse(0.0);
        }
        return 0.0;
    }
    
    /**
     * Enregistre une transaction et met à jour les soldes
     */
    @Transactional
    public HistoriqueSolde enregistrerTransaction(
            String type, // "FACTURE_VENTE", "FACTURE_ACHAT", "PAIEMENT_CLIENT", "PAIEMENT_FOURNISSEUR"
            Double montant,
            String partenaireId,
            String partenaireType, // "CLIENT" ou "FOURNISSEUR"
            String partenaireNom,
            String referenceId,
            String referenceNumero,
            String description
    ) {
        return enregistrerTransaction(type, montant, partenaireId, partenaireType, partenaireNom, 
                referenceId, referenceNumero, description, null);
    }
    
    /**
     * Enregistre une transaction et met à jour les soldes avec une date spécifique
     */
    @Transactional
    public HistoriqueSolde enregistrerTransaction(
            String type, // "FACTURE_VENTE", "FACTURE_ACHAT", "PAIEMENT_CLIENT", "PAIEMENT_FOURNISSEUR"
            Double montant,
            String partenaireId,
            String partenaireType, // "CLIENT" ou "FOURNISSEUR"
            String partenaireNom,
            String referenceId,
            String referenceNumero,
            String description,
            LocalDate dateTransaction // Date de la transaction (colonne DATE de l'Excel)
    ) {
        // Récupérer le solde global actuel
        Double soldeGlobalAvant = getSoldeGlobalActuel();
        Double soldePartenaireAvant = getSoldePartenaire(partenaireId, partenaireType);
        
        // Calculer les nouveaux soldes selon le type de transaction
        Double soldeGlobalApres = calculerSoldeGlobal(type, soldeGlobalAvant, montant);
        Double soldePartenaireApres = calculerSoldePartenaire(type, soldePartenaireAvant, montant);
        
        // Mettre à jour le solde global
        mettreAJourSoldeGlobal(soldeGlobalApres);
        
        // Mettre à jour le solde partenaire
        mettreAJourSoldePartenaire(partenaireId, partenaireType, soldePartenaireApres);
        
        // Utiliser la date de la transaction si fournie, sinon utiliser maintenant
        LocalDateTime dateHistorique = dateTransaction != null 
                ? dateTransaction.atStartOfDay() 
                : LocalDateTime.now();
        
        // Créer l'historique
        HistoriqueSolde historique = HistoriqueSolde.builder()
                .type(type)
                .montant(montant)
                .soldeGlobalAvant(soldeGlobalAvant)
                .soldeGlobalApres(soldeGlobalApres)
                .soldePartenaireAvant(soldePartenaireAvant)
                .soldePartenaireApres(soldePartenaireApres)
                .partenaireId(partenaireId)
                .partenaireType(partenaireType)
                .partenaireNom(partenaireNom)
                .referenceId(referenceId)
                .referenceNumero(referenceNumero)
                .description(description)
                .date(dateHistorique)
                .build();
        
        return historiqueSoldeRepository.save(historique);
    }
    
    /**
     * Calcule le nouveau solde global selon le type de transaction
     */
    private Double calculerSoldeGlobal(String type, Double soldeAvant, Double montant) {
        switch (type) {
            case "FACTURE_VENTE":
                // Facture vente : le client nous doit de l'argent, mais ça n'augmente pas la trésorerie immédiatement
                // Le solde global ne change pas jusqu'au paiement
                return soldeAvant;
            case "PAIEMENT_CLIENT":
                // Paiement client : entrée d'argent, augmente la trésorerie
                return soldeAvant + montant;
            case "FACTURE_ACHAT":
                // Facture achat : nous devons au fournisseur, mais ça ne diminue pas la trésorerie immédiatement
                // Le solde global ne change pas jusqu'au paiement
                return soldeAvant;
            case "PAIEMENT_FOURNISSEUR":
                // Paiement fournisseur : sortie d'argent, diminue la trésorerie
                return soldeAvant - montant;
            case "APPORT_EXTERNE":
                // Apport externe : entrée d'argent depuis l'extérieur, augmente la trésorerie
                return soldeAvant + montant;
            default:
                return soldeAvant;
        }
    }
    
    /**
     * Calcule le nouveau solde partenaire selon le type de transaction
     */
    private Double calculerSoldePartenaire(String type, Double soldeAvant, Double montant) {
        switch (type) {
            case "FACTURE_VENTE":
                // Facture vente : le client nous doit de l'argent (solde client augmente)
                return (soldeAvant != null ? soldeAvant : 0.0) + montant;
            case "PAIEMENT_CLIENT":
                // Paiement client : le client rembourse (solde client diminue)
                return (soldeAvant != null ? soldeAvant : 0.0) - montant;
            case "FACTURE_ACHAT":
                // Facture achat : nous devons au fournisseur (solde fournisseur augmente)
                return (soldeAvant != null ? soldeAvant : 0.0) + montant;
            case "PAIEMENT_FOURNISSEUR":
                // Paiement fournisseur : nous remboursons (solde fournisseur diminue)
                return (soldeAvant != null ? soldeAvant : 0.0) - montant;
            default:
                return soldeAvant != null ? soldeAvant : 0.0;
        }
    }
    
    /**
     * Met à jour le solde global
     */
    private void mettreAJourSoldeGlobal(Double nouveauSolde) {
        List<SoldeGlobal> existing = soldeGlobalRepository.findAll();
        SoldeGlobal soldeGlobal;
        
        if (existing.isEmpty()) {
            soldeGlobal = SoldeGlobal.builder()
                    .soldeInitial(0.0)
                    .soldeActuel(nouveauSolde)
                    .dateDebut(LocalDate.now())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        } else {
            soldeGlobal = existing.get(0);
            soldeGlobal.setSoldeActuel(nouveauSolde);
            soldeGlobal.setUpdatedAt(LocalDateTime.now());
        }
        
        soldeGlobalRepository.save(soldeGlobal);
    }
    
    /**
     * Met à jour le solde d'un partenaire
     */
    private void mettreAJourSoldePartenaire(String partenaireId, String partenaireType, Double nouveauSolde) {
        if ("CLIENT".equals(partenaireType)) {
            clientRepository.findById(partenaireId).ifPresent(client -> {
                client.setSoldeClient(nouveauSolde);
                client.setUpdatedAt(LocalDateTime.now());
                clientRepository.save(client);
            });
        } else if ("FOURNISSEUR".equals(partenaireType)) {
            supplierRepository.findById(partenaireId).ifPresent(supplier -> {
                supplier.setSoldeFournisseur(nouveauSolde);
                supplier.setUpdatedAt(LocalDateTime.now());
                supplierRepository.save(supplier);
            });
        }
    }
    
    /**
     * Récupère l'historique des transactions avec filtres optionnels
     */
    public List<HistoriqueSolde> getHistorique(
            String partenaireId,
            String partenaireType,
            String type,
            LocalDateTime dateDebut,
            LocalDateTime dateFin
    ) {
        if (partenaireId != null && partenaireType != null) {
            return historiqueSoldeRepository.findByPartenaireIdAndPartenaireTypeOrderByDateDesc(partenaireId, partenaireType);
        } else if (type != null) {
            return historiqueSoldeRepository.findByTypeOrderByDateDesc(type);
        } else if (dateDebut != null && dateFin != null) {
            return historiqueSoldeRepository.findByDateBetweenOrderByDateDesc(dateDebut, dateFin);
        } else {
            return historiqueSoldeRepository.findAllByOrderByDateDesc();
        }
    }
    
    /**
     * Récupère le solde global complet (avec solde initial)
     * Si aucun solde n'existe mais qu'il y a un historique, recalcule depuis l'historique
     */
    public Optional<SoldeGlobal> getSoldeGlobal() {
        Optional<SoldeGlobal> soldeOpt = soldeGlobalRepository.findAll().stream().findFirst();
        
        // Si aucun solde n'existe mais qu'il y a un historique, recalculer depuis l'historique
        if (soldeOpt.isEmpty()) {
            List<HistoriqueSolde> historique = historiqueSoldeRepository.findAllByOrderByDateDesc();
            if (!historique.isEmpty()) {
                // Prendre le solde global après de la dernière transaction
                HistoriqueSolde dernier = historique.get(0);
                if (dernier.getSoldeGlobalApres() != null) {
                    // Créer un solde global à partir de l'historique
                    SoldeGlobal soldeGlobal = SoldeGlobal.builder()
                            .soldeInitial(dernier.getSoldeGlobalApres())
                            .soldeActuel(dernier.getSoldeGlobalApres())
                            .dateDebut(dernier.getDate() != null ? dernier.getDate().toLocalDate() : LocalDate.now())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    soldeGlobal = soldeGlobalRepository.save(soldeGlobal);
                    return Optional.of(soldeGlobal);
                }
            }
        } else {
            // Vérifier si le solde actuel est cohérent avec l'historique
            SoldeGlobal solde = soldeOpt.get();
            List<HistoriqueSolde> historique = historiqueSoldeRepository.findAllByOrderByDateDesc();
            if (!historique.isEmpty()) {
                HistoriqueSolde dernier = historique.get(0);
                if (dernier.getSoldeGlobalApres() != null && 
                    !dernier.getSoldeGlobalApres().equals(solde.getSoldeActuel())) {
                    // Le solde n'est pas à jour, le mettre à jour
                    solde.setSoldeActuel(dernier.getSoldeGlobalApres());
                    solde.setUpdatedAt(LocalDateTime.now());
                    solde = soldeGlobalRepository.save(solde);
                    return Optional.of(solde);
                }
            }
        }
        
        return soldeOpt;
    }
    
    /**
     * Ajoute un apport externe (augmentation de capital, prêt, etc.) au solde global
     * 
     * @param montant Montant de l'apport
     * @param motif Motif de l'apport (ex: "Augmentation de capital", "Prêt", etc.)
     * @param date Date de l'apport (optionnel, défaut = maintenant)
     * @return HistoriqueSolde créé
     */
    @Transactional
    public HistoriqueSolde ajouterApportExterne(Double montant, String motif, LocalDate date) {
        if (montant == null || montant <= 0) {
            throw new IllegalArgumentException("Le montant doit être positif");
        }
        if (motif == null || motif.trim().isEmpty()) {
            throw new IllegalArgumentException("Le motif est requis");
        }
        
        // Récupérer le solde global actuel
        Double soldeGlobalAvant = getSoldeGlobalActuel();
        Double soldeGlobalApres = soldeGlobalAvant + montant;
        
        // Mettre à jour le solde global
        mettreAJourSoldeGlobal(soldeGlobalApres);
        
        // Créer l'historique
        HistoriqueSolde historique = HistoriqueSolde.builder()
                .type("APPORT_EXTERNE")
                .montant(montant)
                .soldeGlobalAvant(soldeGlobalAvant)
                .soldeGlobalApres(soldeGlobalApres)
                .soldePartenaireAvant(null)
                .soldePartenaireApres(null)
                .partenaireId(null)
                .partenaireType(null)
                .partenaireNom(null)
                .referenceId(null)
                .referenceNumero(null)
                .description(motif.trim())
                .date(date != null ? date.atStartOfDay() : LocalDateTime.now())
                .build();
        
        HistoriqueSolde saved = historiqueSoldeRepository.save(historique);
        log.info("Apport externe enregistré: {} MAD - Motif: {} - Solde après: {}", montant, motif, soldeGlobalApres);
        
        return saved;
    }

    /**
     * Ajoute une charge payée (sortie de trésorerie) et enregistre l'historique.
     * L'imposable = déductible fiscalement.
     */
    @Transactional
    public HistoriqueSolde ajouterCharge(Double montant, Boolean imposable, String chargeId, String libelle, LocalDate datePaiement) {
        if (montant == null || montant <= 0) {
            throw new IllegalArgumentException("Le montant doit être positif");
        }
        if (libelle == null || libelle.trim().isEmpty()) {
            throw new IllegalArgumentException("Le libellé est requis");
        }

        Double soldeGlobalAvant = getSoldeGlobalActuel();
        Double soldeGlobalApres = soldeGlobalAvant - montant;

        // Mettre à jour le solde global
        mettreAJourSoldeGlobal(soldeGlobalApres);

        String type = Boolean.TRUE.equals(imposable) ? "CHARGE_IMPOSABLE" : "CHARGE_NON_IMPOSABLE";

        HistoriqueSolde historique = HistoriqueSolde.builder()
                .type(type)
                .montant(montant)
                .soldeGlobalAvant(soldeGlobalAvant)
                .soldeGlobalApres(soldeGlobalApres)
                .soldePartenaireAvant(null)
                .soldePartenaireApres(null)
                .partenaireId(null)
                .partenaireType(null)
                .partenaireNom(null)
                .referenceId(chargeId)
                .referenceNumero(libelle.trim())
                .description(libelle.trim())
                .date(LocalDateTime.now())
                .build();

        HistoriqueSolde saved = historiqueSoldeRepository.save(historique);
        log.info("Charge payée enregistrée: {} MAD - {} - Solde après: {}", montant, libelle, soldeGlobalApres);
        return saved;
    }
}

