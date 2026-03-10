package com.bf4invest.service;

import com.bf4invest.model.*;
import com.bf4invest.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComptabiliteService {

    private final CompteComptableRepository compteRepository;
    private final ExerciceComptableRepository exerciceRepository;
    private final EcritureComptableRepository ecritureRepository;
    private final FactureVenteRepository factureVenteRepository;
    private final FactureAchatRepository factureAchatRepository;
    private final PaiementRepository paiementRepository;
    private final ChargeRepository chargeRepository;

    /**
     * Initialise le plan comptable PCGM standard
     */
    public void initializePlanComptable() {
        if (compteRepository.count() > 0) {
            log.info("Plan comptable déjà initialisé, skip");
            return;
        }

        List<CompteComptable> comptes = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // Classe 1 - Capitaux propres
        comptes.add(createCompte("1111", "Capital social", "1", CompteComptable.TypeCompte.PASSIF, false, null, now));
        comptes.add(createCompte("1140", "Réserves", "1", CompteComptable.TypeCompte.PASSIF, false, null, now));
        comptes.add(createCompte("1151", "Résultat net de l'exercice", "1", CompteComptable.TypeCompte.PASSIF, false, null, now));
        comptes.add(createCompte("1181", "Report à nouveau", "1", CompteComptable.TypeCompte.PASSIF, false, null, now));

        // Classe 2 - Immobilisations
        comptes.add(createCompte("2111", "Terrains", "2", CompteComptable.TypeCompte.ACTIF, false, null, now));
        comptes.add(createCompte("2112", "Constructions", "2", CompteComptable.TypeCompte.ACTIF, false, null, now));
        comptes.add(createCompte("2230", "Matériel de transport", "2", CompteComptable.TypeCompte.ACTIF, false, null, now));
        comptes.add(createCompte("2240", "Matériel informatique", "2", CompteComptable.TypeCompte.ACTIF, false, null, now));
        comptes.add(createCompte("2811", "Amortissements terrains", "2", CompteComptable.TypeCompte.ACTIF, false, null, now));
        comptes.add(createCompte("2812", "Amortissements constructions", "2", CompteComptable.TypeCompte.ACTIF, false, null, now));

        // Classe 3 - Stocks
        comptes.add(createCompte("3111", "Matières premières", "3", CompteComptable.TypeCompte.ACTIF, false, null, now));
        comptes.add(createCompte("3112", "Matières et fournitures consommables", "3", CompteComptable.TypeCompte.ACTIF, false, null, now));
        comptes.add(createCompte("3121", "Produits en cours", "3", CompteComptable.TypeCompte.ACTIF, false, null, now));
        comptes.add(createCompte("3131", "Produits finis", "3", CompteComptable.TypeCompte.ACTIF, false, null, now));
        comptes.add(createCompte("3151", "Marchandises", "3", CompteComptable.TypeCompte.ACTIF, false, null, now));

        // Classe 4 - Tiers
        comptes.add(createCompte("4111", "Clients", "4", CompteComptable.TypeCompte.ACTIF, false, null, now));
        comptes.add(createCompte("41111", "Clients - Ventes", "4", CompteComptable.TypeCompte.ACTIF, false, "4111", now));
        comptes.add(createCompte("41119", "Clients - Autres", "4", CompteComptable.TypeCompte.ACTIF, false, "4111", now));
        comptes.add(createCompte("4411", "Fournisseurs", "4", CompteComptable.TypeCompte.PASSIF, false, null, now));
        comptes.add(createCompte("44111", "Fournisseurs - Achats", "4", CompteComptable.TypeCompte.PASSIF, false, "4411", now));
        comptes.add(createCompte("44119", "Fournisseurs - Autres", "4", CompteComptable.TypeCompte.PASSIF, false, "4411", now));
        comptes.add(createCompte("4455", "TVA à payer", "4", CompteComptable.TypeCompte.PASSIF, false, null, now));
        comptes.add(createCompte("4456", "TVA déductible", "4", CompteComptable.TypeCompte.ACTIF, false, null, now));
        comptes.add(createCompte("4457", "TVA collectée", "4", CompteComptable.TypeCompte.PASSIF, false, null, now));
        comptes.add(createCompte("4441", "État - Impôts sur les bénéfices", "4", CompteComptable.TypeCompte.PASSIF, false, null, now));
        comptes.add(createCompte("4442", "État - Autres impôts", "4", CompteComptable.TypeCompte.PASSIF, false, null, now));
        comptes.add(createCompte("4443", "État - TVA à payer", "4", CompteComptable.TypeCompte.PASSIF, false, null, now));
        comptes.add(createCompte("4444", "État - TVA crédit", "4", CompteComptable.TypeCompte.ACTIF, false, null, now));
        comptes.add(createCompte("4211", "Personnel - Rémunérations dues", "4", CompteComptable.TypeCompte.PASSIF, false, null, now));
        comptes.add(createCompte("4251", "Organismes sociaux - Charges à payer", "4", CompteComptable.TypeCompte.PASSIF, false, null, now));

        // Classe 5 - Trésorerie
        comptes.add(createCompte("5141", "Banques", "5", CompteComptable.TypeCompte.TRESORERIE, false, null, now));
        comptes.add(createCompte("5161", "Caisse", "5", CompteComptable.TypeCompte.TRESORERIE, false, null, now));
        comptes.add(createCompte("5311", "Valeurs mobilières de placement", "5", CompteComptable.TypeCompte.ACTIF, false, null, now));

        // Classe 6 - Charges
        comptes.add(createCompte("6111", "Achats de matières premières", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6112", "Achats de matières et fournitures consommables", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6113", "Achats d'emballages", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6114", "Achats de marchandises", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6121", "Variations de stocks de matières premières", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6122", "Variations de stocks de matières et fournitures", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6123", "Variations de stocks d'emballages", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6124", "Variations de stocks de marchandises", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6131", "Services extérieurs", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6132", "Locations et charges locatives", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6133", "Assurances", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6134", "Charges de personnel extérieur", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6135", "Publicité, publications et relations publiques", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6136", "Transports", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6137", "Télécommunications", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6138", "Eau, électricité et autres charges", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6171", "Salaires", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6172", "Charges sociales", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6173", "Autres charges de personnel", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6181", "Dotations aux amortissements", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6182", "Dotations aux provisions", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6191", "Pertes sur créances irrécouvrables", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6211", "Intérêts et charges assimilées", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6311", "Impôts, taxes et droits assimilés", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6312", "Impôt sur les bénéfices", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6511", "Pertes sur créances clients", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));
        comptes.add(createCompte("6711", "Charges exceptionnelles", "6", CompteComptable.TypeCompte.CHARGE, false, null, now));

        // Classe 7 - Produits
        comptes.add(createCompte("7111", "Ventes de produits finis", "7", CompteComptable.TypeCompte.PRODUIT, false, null, now));
        comptes.add(createCompte("7112", "Ventes de produits intermédiaires", "7", CompteComptable.TypeCompte.PRODUIT, false, null, now));
        comptes.add(createCompte("7113", "Ventes de produits résiduels", "7", CompteComptable.TypeCompte.PRODUIT, false, null, now));
        comptes.add(createCompte("7121", "Ventes de marchandises", "7", CompteComptable.TypeCompte.PRODUIT, false, null, now));
        comptes.add(createCompte("7131", "Variations de stocks de produits finis", "7", CompteComptable.TypeCompte.PRODUIT, false, null, now));
        comptes.add(createCompte("7132", "Variations de stocks de produits intermédiaires", "7", CompteComptable.TypeCompte.PRODUIT, false, null, now));
        comptes.add(createCompte("7133", "Variations de stocks de produits résiduels", "7", CompteComptable.TypeCompte.PRODUIT, false, null, now));
        comptes.add(createCompte("7134", "Variations de stocks de marchandises", "7", CompteComptable.TypeCompte.PRODUIT, false, null, now));
        comptes.add(createCompte("7211", "Produits accessoires", "7", CompteComptable.TypeCompte.PRODUIT, false, null, now));
        comptes.add(createCompte("7311", "Revenus des créances", "7", CompteComptable.TypeCompte.PRODUIT, false, null, now));
        comptes.add(createCompte("7411", "Subventions d'exploitation", "7", CompteComptable.TypeCompte.PRODUIT, false, null, now));
        comptes.add(createCompte("7511", "Produits exceptionnels", "7", CompteComptable.TypeCompte.PRODUIT, false, null, now));
        comptes.add(createCompte("7611", "Produits financiers", "7", CompteComptable.TypeCompte.PRODUIT, false, null, now));

        compteRepository.saveAll(comptes);
        log.info("Plan comptable PCGM initialisé avec {} comptes", comptes.size());
    }

    private CompteComptable createCompte(String code, String libelle, String classe, 
                                         CompteComptable.TypeCompte type, Boolean collectif, 
                                         String compteParent, LocalDateTime now) {
        return CompteComptable.builder()
                .code(code)
                .libelle(libelle)
                .classe(classe)
                .type(type)
                .collectif(collectif != null ? collectif : false)
                .compteParent(compteParent)
                .soldeDebit(0.0)
                .soldeCredit(0.0)
                .solde(0.0)
                .actif(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * S'assure que les comptes essentiels pour les écritures comptables existent
     * Crée les comptes manquants si nécessaire
     */
    private void ensureEssentialAccountsExist() {
        LocalDateTime now = LocalDateTime.now();
        boolean needsSave = false;
        List<CompteComptable> toSave = new ArrayList<>();

        // Compte 5141 - Banques (essentiel pour les paiements)
        if (compteRepository.findByCode("5141").isEmpty()) {
            log.warn("Compte banque (5141) manquant, création automatique");
            toSave.add(createCompte("5141", "Banques", "5", CompteComptable.TypeCompte.TRESORERIE, false, null, now));
            needsSave = true;
        }

        // Compte 41111 - Clients - Ventes (pour les paiements clients)
        if (compteRepository.findByCode("41111").isEmpty()) {
            log.warn("Compte client (41111) manquant, création automatique");
            // S'assurer que le compte parent 4111 existe aussi
            if (compteRepository.findByCode("4111").isEmpty()) {
                toSave.add(createCompte("4111", "Clients", "4", CompteComptable.TypeCompte.ACTIF, false, null, now));
            }
            toSave.add(createCompte("41111", "Clients - Ventes", "4", CompteComptable.TypeCompte.ACTIF, false, "4111", now));
            needsSave = true;
        }

        // Compte 44111 - Fournisseurs - Achats (pour les paiements fournisseurs)
        if (compteRepository.findByCode("44111").isEmpty()) {
            log.warn("Compte fournisseur (44111) manquant, création automatique");
            // S'assurer que le compte parent 4411 existe aussi
            if (compteRepository.findByCode("4411").isEmpty()) {
                toSave.add(createCompte("4411", "Fournisseurs", "4", CompteComptable.TypeCompte.PASSIF, false, null, now));
            }
            toSave.add(createCompte("44111", "Fournisseurs - Achats", "4", CompteComptable.TypeCompte.PASSIF, false, "4411", now));
            needsSave = true;
        }

        if (needsSave) {
            compteRepository.saveAll(toSave);
            log.info("{} compte(s) essentiel(s) créé(s) automatiquement", toSave.size());
        }
    }

    /**
     * Crée ou récupère l'exercice comptable pour l'année en cours
     */
    public ExerciceComptable getOrCreateCurrentExercice() {
        int currentYear = LocalDate.now().getYear();
        String code = String.valueOf(currentYear);
        
        Optional<ExerciceComptable> existing = exerciceRepository.findByCode(code);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        ExerciceComptable exercice = ExerciceComptable.builder()
                .code(code)
                .dateDebut(LocalDate.of(currentYear, 1, 1))
                .dateFin(LocalDate.of(currentYear, 12, 31))
                .statut(ExerciceComptable.StatutExercice.OUVERT)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        return exerciceRepository.save(exercice);
    }

    /**
     * Crée un nouvel exercice comptable avec validation
     * Règles:
     * - Unicité: pas de chevauchement de dates avec un exercice existant
     * - Validité: dateDebut < dateFin
     * - Code: généré automatiquement si non fourni (format: "YYYY" ou "YYYY-YYYY")
     * - Statut: OUVERT par défaut
     */
    @Transactional
    public ExerciceComptable createExercice(ExerciceComptable exercice) {
        // Validation des dates
        if (exercice.getDateDebut() == null || exercice.getDateFin() == null) {
            throw new IllegalArgumentException("Les dates de début et de fin sont obligatoires");
        }
        
        if (!exercice.getDateDebut().isBefore(exercice.getDateFin())) {
            throw new IllegalArgumentException("La date de début doit être antérieure à la date de fin");
        }
        
        // Générer le code si non fourni
        if (exercice.getCode() == null || exercice.getCode().trim().isEmpty()) {
            int anneeDebut = exercice.getDateDebut().getYear();
            int anneeFin = exercice.getDateFin().getYear();
            
            if (anneeDebut == anneeFin) {
                exercice.setCode(String.valueOf(anneeDebut));
            } else {
                exercice.setCode(anneeDebut + "-" + anneeFin);
            }
        }
        
        // Vérifier l'unicité du code
        Optional<ExerciceComptable> existingByCode = exerciceRepository.findByCode(exercice.getCode());
        if (existingByCode.isPresent()) {
            throw new IllegalArgumentException(
                String.format("Un exercice avec le code '%s' existe déjà", exercice.getCode())
            );
        }
        
        // Vérifier qu'il n'y a pas de chevauchement de dates
        // Un exercice chevauche si:
        // - Sa date de début est dans la plage d'un autre exercice, OU
        // - Sa date de fin est dans la plage d'un autre exercice, OU
        // - Il englobe complètement un autre exercice
        List<ExerciceComptable> allExercices = exerciceRepository.findAll();
        for (ExerciceComptable existing : allExercices) {
            boolean chevauche = 
                // Nouvel exercice commence dans un existant
                (!exercice.getDateDebut().isBefore(existing.getDateDebut()) && 
                 !exercice.getDateDebut().isAfter(existing.getDateFin())) ||
                // Nouvel exercice se termine dans un existant
                (!exercice.getDateFin().isBefore(existing.getDateDebut()) && 
                 !exercice.getDateFin().isAfter(existing.getDateFin())) ||
                // Nouvel exercice englobe complètement un existant
                (exercice.getDateDebut().isBefore(existing.getDateDebut()) && 
                 exercice.getDateFin().isAfter(existing.getDateFin()));
            
            if (chevauche) {
                throw new IllegalArgumentException(
                    String.format(
                        "L'exercice chevauche avec l'exercice existant '%s' (%s - %s)",
                        existing.getCode(),
                        existing.getDateDebut(),
                        existing.getDateFin()
                    )
                );
            }
        }
        
        // Définir le statut par défaut si non fourni
        if (exercice.getStatut() == null) {
            exercice.setStatut(ExerciceComptable.StatutExercice.OUVERT);
        }
        
        // Timestamps
        LocalDateTime now = LocalDateTime.now();
        exercice.setCreatedAt(now);
        exercice.setUpdatedAt(now);
        
        ExerciceComptable saved = exerciceRepository.save(exercice);
        log.info("Exercice comptable créé: {} ({})", saved.getCode(), saved.getDateDebut() + " - " + saved.getDateFin());
        
        return saved;
    }

    /**
     * Récupère un compte par son code
     */
    public Optional<CompteComptable> getCompteByCode(String code) {
        return compteRepository.findByCode(code);
    }

    /**
     * Récupère tous les comptes actifs
     */
    public List<CompteComptable> getAllComptesActifs() {
        return compteRepository.findByActifTrue();
    }

    /**
     * Crée un nouveau compte comptable à partir d'un DTO.
     * Valide l'unicité du code et initialise les soldes à 0.
     */
    public CompteComptable createCompte(CompteComptable dto) {
        if (dto.getCode() == null || dto.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Le code du compte est obligatoire");
        }
        String code = dto.getCode().trim();
        compteRepository.findByCode(code).ifPresent(existing -> {
            throw new IllegalArgumentException("Un compte avec le code " + code + " existe déjà");
        });

        LocalDateTime now = LocalDateTime.now();

        CompteComptable compte = CompteComptable.builder()
                .code(code)
                .libelle(dto.getLibelle())
                .classe(dto.getClasse())
                .type(dto.getType())
                .collectif(dto.getCollectif() != null ? dto.getCollectif() : Boolean.FALSE)
                .compteParent(dto.getCompteParent())
                .soldeDebit(0.0)
                .soldeCredit(0.0)
                .solde(0.0)
                .actif(dto.getActif() != null ? dto.getActif() : Boolean.TRUE)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return compteRepository.save(compte);
    }

    /**
     * Met à jour un compte existant (libellé, type, parent, actif...).
     * Ne permet pas de modifier les soldes directement.
     */
    public CompteComptable updateCompte(String id, CompteComptable dto) {
        CompteComptable compte = compteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Compte introuvable pour id " + id));

        if (dto.getLibelle() != null) {
            compte.setLibelle(dto.getLibelle());
        }
        if (dto.getClasse() != null) {
            compte.setClasse(dto.getClasse());
        }
        if (dto.getType() != null) {
            compte.setType(dto.getType());
        }
        if (dto.getCollectif() != null) {
            compte.setCollectif(dto.getCollectif());
        }
        if (dto.getCompteParent() != null) {
            compte.setCompteParent(dto.getCompteParent());
        }
        if (dto.getActif() != null) {
            compte.setActif(dto.getActif());
        }

        compte.setUpdatedAt(LocalDateTime.now());
        return compteRepository.save(compte);
    }

    /**
     * Désactive un compte comptable (soft delete).
     */
    public CompteComptable deactivateCompte(String id) {
        CompteComptable compte = compteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Compte introuvable pour id " + id));
        compte.setActif(false);
        compte.setUpdatedAt(LocalDateTime.now());
        return compteRepository.save(compte);
    }

    /**
     * Génère une écriture comptable pour une facture vente
     * Débit: 41111 (Clients - Ventes) / Crédit: 7121 (Ventes de marchandises) + 4457 (TVA collectée)
     */
    @Transactional
    public EcritureComptable genererEcritureFactureVente(FactureVente facture) {
        // Vérifier si l'écriture existe déjà
        List<EcritureComptable> existing = ecritureRepository.findByPieceJustificativeTypeAndPieceJustificativeId(
                "FACTURE_VENTE", facture.getId());
        if (!existing.isEmpty()) {
            log.debug("Écriture déjà existante pour facture vente {}", facture.getId());
            return existing.get(0);
        }

        ExerciceComptable exercice = getExerciceForDate(facture.getDateFacture());
        if (exercice == null) {
            log.warn("Aucun exercice trouvé pour la date {}", facture.getDateFacture());
            return null;
        }

        Optional<CompteComptable> compteClient = compteRepository.findByCode("41111");
        Optional<CompteComptable> compteVentes = compteRepository.findByCode("7121");
        Optional<CompteComptable> compteTVACollectee = compteRepository.findByCode("4457");

        if (compteClient.isEmpty() || compteVentes.isEmpty() || compteTVACollectee.isEmpty()) {
            log.error("Comptes comptables manquants pour générer l'écriture de facture vente");
            return null;
        }

        Double montantHT = facture.getTotalHT() != null ? facture.getTotalHT() : 0.0;
        Double montantTVA = facture.getTotalTVA() != null ? facture.getTotalTVA() : 0.0;
        Double montantTTC = facture.getTotalTTC() != null ? facture.getTotalTTC() : montantHT + montantTVA;

        List<LigneEcriture> lignes = new ArrayList<>();
        
        // Débit client
        lignes.add(LigneEcriture.builder()
                .compteCode(compteClient.get().getCode())
                .compteLibelle(compteClient.get().getLibelle())
                .debit(montantTTC)
                .credit(null)
                .libelle("Facture " + facture.getNumeroFactureVente())
                .build());

        // Crédit ventes HT
        lignes.add(LigneEcriture.builder()
                .compteCode(compteVentes.get().getCode())
                .compteLibelle(compteVentes.get().getLibelle())
                .debit(null)
                .credit(montantHT)
                .libelle("Vente " + facture.getNumeroFactureVente())
                .build());

        // Crédit TVA collectée
        if (montantTVA > 0) {
            lignes.add(LigneEcriture.builder()
                    .compteCode(compteTVACollectee.get().getCode())
                    .compteLibelle(compteTVACollectee.get().getLibelle())
                    .debit(null)
                    .credit(montantTVA)
                    .libelle("TVA collectée " + facture.getNumeroFactureVente())
                    .build());
        }

        EcritureComptable ecriture = EcritureComptable.builder()
                .dateEcriture(facture.getDateFacture())
                .journal("VT")
                .numeroPiece("FV-" + facture.getNumeroFactureVente())
                .libelle("Facture vente " + facture.getNumeroFactureVente())
                .lignes(lignes)
                .pieceJustificativeType("FACTURE_VENTE")
                .pieceJustificativeId(facture.getId())
                .exerciceId(exercice.getId())
                .lettree(false)
                .pointage(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        EcritureComptable saved = ecritureRepository.save(ecriture);
        updateComptesSoldes(lignes);
        log.info("Écriture générée pour facture vente {}", facture.getNumeroFactureVente());
        return saved;
    }

    /**
     * Génère une écriture comptable pour une facture achat
     * Débit: 6114 (Achats de marchandises) + 4456 (TVA déductible) / Crédit: 44111 (Fournisseurs - Achats)
     */
    @Transactional
    public EcritureComptable genererEcritureFactureAchat(FactureAchat facture) {
        // Vérifier si l'écriture existe déjà
        List<EcritureComptable> existing = ecritureRepository.findByPieceJustificativeTypeAndPieceJustificativeId(
                "FACTURE_ACHAT", facture.getId());
        if (!existing.isEmpty()) {
            log.debug("Écriture déjà existante pour facture achat {}", facture.getId());
            return existing.get(0);
        }

        ExerciceComptable exercice = getExerciceForDate(facture.getDateFacture());
        if (exercice == null) {
            log.warn("Aucun exercice trouvé pour la date {}", facture.getDateFacture());
            return null;
        }

        Optional<CompteComptable> compteAchats = compteRepository.findByCode("6114");
        Optional<CompteComptable> compteTVADeductible = compteRepository.findByCode("4456");
        Optional<CompteComptable> compteFournisseur = compteRepository.findByCode("44111");

        if (compteAchats.isEmpty() || compteTVADeductible.isEmpty() || compteFournisseur.isEmpty()) {
            log.error("Comptes comptables manquants pour générer l'écriture de facture achat");
            return null;
        }

        Double montantHT = facture.getTotalHT() != null ? facture.getTotalHT() : 0.0;
        Double montantTVA = facture.getTotalTVA() != null ? facture.getTotalTVA() : 0.0;
        Double montantTTC = facture.getTotalTTC() != null ? facture.getTotalTTC() : montantHT + montantTVA;

        List<LigneEcriture> lignes = new ArrayList<>();
        
        // Débit achats HT
        lignes.add(LigneEcriture.builder()
                .compteCode(compteAchats.get().getCode())
                .compteLibelle(compteAchats.get().getLibelle())
                .debit(montantHT)
                .credit(null)
                .libelle("Achat " + facture.getNumeroFactureAchat())
                .build());

        // Débit TVA déductible
        if (montantTVA > 0) {
            lignes.add(LigneEcriture.builder()
                    .compteCode(compteTVADeductible.get().getCode())
                    .compteLibelle(compteTVADeductible.get().getLibelle())
                    .debit(montantTVA)
                    .credit(null)
                    .libelle("TVA déductible " + facture.getNumeroFactureAchat())
                    .build());
        }

        // Crédit fournisseur
        lignes.add(LigneEcriture.builder()
                .compteCode(compteFournisseur.get().getCode())
                .compteLibelle(compteFournisseur.get().getLibelle())
                .debit(null)
                .credit(montantTTC)
                .libelle("Facture " + facture.getNumeroFactureAchat())
                .build());

        EcritureComptable ecriture = EcritureComptable.builder()
                .dateEcriture(facture.getDateFacture())
                .journal("AC")
                .numeroPiece("FA-" + facture.getNumeroFactureAchat())
                .libelle("Facture achat " + facture.getNumeroFactureAchat())
                .lignes(lignes)
                .pieceJustificativeType("FACTURE_ACHAT")
                .pieceJustificativeId(facture.getId())
                .exerciceId(exercice.getId())
                .lettree(false)
                .pointage(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        EcritureComptable saved = ecritureRepository.save(ecriture);
        updateComptesSoldes(lignes);
        log.info("Écriture générée pour facture achat {}", facture.getNumeroFactureAchat());
        return saved;
    }

    /**
     * Génère une écriture comptable pour un avoir vente
     * Les avoirs inversent les écritures : Crédit Client / Débit Ventes / Débit TVA collectée
     * Les montants sont déjà négatifs dans la facture, on utilise leur valeur absolue
     */
    @Transactional
    public EcritureComptable genererEcritureAvoirVente(FactureVente avoir) {
        // Vérifier si l'écriture existe déjà
        List<EcritureComptable> existing = ecritureRepository.findByPieceJustificativeTypeAndPieceJustificativeId(
                "AVOIR_VENTE", avoir.getId());
        if (!existing.isEmpty()) {
            log.debug("Écriture déjà existante pour avoir vente {}", avoir.getId());
            return existing.get(0);
        }

        ExerciceComptable exercice = getExerciceForDate(avoir.getDateFacture());
        if (exercice == null) {
            log.warn("Aucun exercice trouvé pour la date {}", avoir.getDateFacture());
            return null;
        }

        Optional<CompteComptable> compteClient = compteRepository.findByCode("41111");
        Optional<CompteComptable> compteVentes = compteRepository.findByCode("7121");
        Optional<CompteComptable> compteTVACollectee = compteRepository.findByCode("4457");

        if (compteClient.isEmpty() || compteVentes.isEmpty() || compteTVACollectee.isEmpty()) {
            log.error("Comptes comptables manquants pour générer l'écriture d'avoir vente");
            return null;
        }

        // Utiliser la valeur absolue des montants (déjà négatifs dans l'avoir)
        Double montantHT = avoir.getTotalHT() != null ? Math.abs(avoir.getTotalHT()) : 0.0;
        Double montantTVA = avoir.getTotalTVA() != null ? Math.abs(avoir.getTotalTVA()) : 0.0;
        Double montantTTC = avoir.getTotalTTC() != null ? Math.abs(avoir.getTotalTTC()) : montantHT + montantTVA;

        List<LigneEcriture> lignes = new ArrayList<>();
        
        // Crédit client (inversé par rapport à facture normale)
        lignes.add(LigneEcriture.builder()
                .compteCode(compteClient.get().getCode())
                .compteLibelle(compteClient.get().getLibelle())
                .debit(null)
                .credit(montantTTC)
                .libelle("Avoir " + avoir.getNumeroFactureVente())
                .build());

        // Débit ventes HT (inversé)
        lignes.add(LigneEcriture.builder()
                .compteCode(compteVentes.get().getCode())
                .compteLibelle(compteVentes.get().getLibelle())
                .debit(montantHT)
                .credit(null)
                .libelle("Avoir vente " + avoir.getNumeroFactureVente())
                .build());

        // Débit TVA collectée (inversé)
        if (montantTVA > 0) {
            lignes.add(LigneEcriture.builder()
                    .compteCode(compteTVACollectee.get().getCode())
                    .compteLibelle(compteTVACollectee.get().getLibelle())
                    .debit(montantTVA)
                    .credit(null)
                    .libelle("TVA collectée avoir " + avoir.getNumeroFactureVente())
                    .build());
        }

        EcritureComptable ecriture = EcritureComptable.builder()
                .dateEcriture(avoir.getDateFacture())
                .journal("VT")
                .numeroPiece("AV-" + avoir.getNumeroFactureVente())
                .libelle("Avoir vente " + avoir.getNumeroFactureVente())
                .lignes(lignes)
                .pieceJustificativeType("AVOIR_VENTE")
                .pieceJustificativeId(avoir.getId())
                .exerciceId(exercice.getId())
                .lettree(false)
                .pointage(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        EcritureComptable saved = ecritureRepository.save(ecriture);
        updateComptesSoldes(lignes);
        log.info("Écriture générée pour avoir vente {}", avoir.getNumeroFactureVente());
        return saved;
    }

    /**
     * Génère une écriture comptable pour un avoir achat
     * Les avoirs inversent les écritures : Crédit Achats / Crédit TVA déductible / Débit Fournisseur
     * Les montants sont déjà négatifs dans la facture, on utilise leur valeur absolue
     */
    @Transactional
    public EcritureComptable genererEcritureAvoirAchat(FactureAchat avoir) {
        // Vérifier si l'écriture existe déjà
        List<EcritureComptable> existing = ecritureRepository.findByPieceJustificativeTypeAndPieceJustificativeId(
                "AVOIR_ACHAT", avoir.getId());
        if (!existing.isEmpty()) {
            log.debug("Écriture déjà existante pour avoir achat {}", avoir.getId());
            return existing.get(0);
        }

        ExerciceComptable exercice = getExerciceForDate(avoir.getDateFacture());
        if (exercice == null) {
            log.warn("Aucun exercice trouvé pour la date {}", avoir.getDateFacture());
            return null;
        }

        Optional<CompteComptable> compteAchats = compteRepository.findByCode("6114");
        Optional<CompteComptable> compteTVADeductible = compteRepository.findByCode("4456");
        Optional<CompteComptable> compteFournisseur = compteRepository.findByCode("44111");

        if (compteAchats.isEmpty() || compteTVADeductible.isEmpty() || compteFournisseur.isEmpty()) {
            log.error("Comptes comptables manquants pour générer l'écriture d'avoir achat");
            return null;
        }

        // Utiliser la valeur absolue des montants (déjà négatifs dans l'avoir)
        Double montantHT = avoir.getTotalHT() != null ? Math.abs(avoir.getTotalHT()) : 0.0;
        Double montantTVA = avoir.getTotalTVA() != null ? Math.abs(avoir.getTotalTVA()) : 0.0;
        Double montantTTC = avoir.getTotalTTC() != null ? Math.abs(avoir.getTotalTTC()) : montantHT + montantTVA;

        List<LigneEcriture> lignes = new ArrayList<>();
        
        // Crédit achats HT (inversé par rapport à facture normale)
        lignes.add(LigneEcriture.builder()
                .compteCode(compteAchats.get().getCode())
                .compteLibelle(compteAchats.get().getLibelle())
                .debit(null)
                .credit(montantHT)
                .libelle("Avoir achat " + avoir.getNumeroFactureAchat())
                .build());

        // Crédit TVA déductible (inversé)
        if (montantTVA > 0) {
            lignes.add(LigneEcriture.builder()
                    .compteCode(compteTVADeductible.get().getCode())
                    .compteLibelle(compteTVADeductible.get().getLibelle())
                    .debit(null)
                    .credit(montantTVA)
                    .libelle("TVA déductible avoir " + avoir.getNumeroFactureAchat())
                    .build());
        }

        // Débit fournisseur (inversé)
        lignes.add(LigneEcriture.builder()
                .compteCode(compteFournisseur.get().getCode())
                .compteLibelle(compteFournisseur.get().getLibelle())
                .debit(montantTTC)
                .credit(null)
                .libelle("Avoir " + avoir.getNumeroFactureAchat())
                .build());

        EcritureComptable ecriture = EcritureComptable.builder()
                .dateEcriture(avoir.getDateFacture())
                .journal("AC")
                .numeroPiece("AV-" + avoir.getNumeroFactureAchat())
                .libelle("Avoir achat " + avoir.getNumeroFactureAchat())
                .lignes(lignes)
                .pieceJustificativeType("AVOIR_ACHAT")
                .pieceJustificativeId(avoir.getId())
                .exerciceId(exercice.getId())
                .lettree(false)
                .pointage(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        EcritureComptable saved = ecritureRepository.save(ecriture);
        updateComptesSoldes(lignes);
        log.info("Écriture générée pour avoir achat {}", avoir.getNumeroFactureAchat());
        return saved;
    }

    /**
     * Génère une écriture comptable pour un paiement
     * Pour paiement client: Débit 5141 (Banques) / Crédit 41111 (Clients)
     * Pour paiement fournisseur: Débit 44111 (Fournisseurs) / Crédit 5141 (Banques)
     */
    @Transactional
    public EcritureComptable genererEcriturePaiement(Paiement paiement) {
        // Vérifier si l'écriture existe déjà
        List<EcritureComptable> existing = ecritureRepository.findByPieceJustificativeTypeAndPieceJustificativeId(
                "PAIEMENT", paiement.getId());
        if (!existing.isEmpty()) {
            log.debug("Écriture déjà existante pour paiement {}", paiement.getId());
            return existing.get(0);
        }

        ExerciceComptable exercice = getExerciceForDate(paiement.getDate());
        if (exercice == null) {
            log.warn("Aucun exercice trouvé pour la date {}", paiement.getDate());
            return null;
        }

        // S'assurer que les comptes essentiels existent
        ensureEssentialAccountsExist();

        Optional<CompteComptable> compteBanque = compteRepository.findByCode("5141");
        Optional<CompteComptable> compteClient = compteRepository.findByCode("41111");
        Optional<CompteComptable> compteFournisseur = compteRepository.findByCode("44111");

        if (compteBanque.isEmpty()) {
            log.error("Compte banque (5141) manquant - impossible de créer après vérification");
            return null;
        }

        Double montant = paiement.getMontant() != null ? paiement.getMontant() : 0.0;
        List<LigneEcriture> lignes = new ArrayList<>();
        String journal = "BQ";
        String libelle = "Paiement " + (paiement.getReference() != null ? paiement.getReference() : "");

        // Déterminer si c'est un paiement client ou fournisseur
        if (paiement.getFactureVenteId() != null && compteClient.isPresent()) {
            // Paiement client: Débit banque, Crédit client
            lignes.add(LigneEcriture.builder()
                    .compteCode(compteBanque.get().getCode())
                    .compteLibelle(compteBanque.get().getLibelle())
                    .debit(montant)
                    .credit(null)
                    .libelle(libelle)
                    .build());
            lignes.add(LigneEcriture.builder()
                    .compteCode(compteClient.get().getCode())
                    .compteLibelle(compteClient.get().getLibelle())
                    .debit(null)
                    .credit(montant)
                    .libelle(libelle)
                    .build());
        } else if (paiement.getFactureAchatId() != null && compteFournisseur.isPresent()) {
            // Paiement fournisseur: Débit fournisseur, Crédit banque
            lignes.add(LigneEcriture.builder()
                    .compteCode(compteFournisseur.get().getCode())
                    .compteLibelle(compteFournisseur.get().getLibelle())
                    .debit(montant)
                    .credit(null)
                    .libelle(libelle)
                    .build());
            lignes.add(LigneEcriture.builder()
                    .compteCode(compteBanque.get().getCode())
                    .compteLibelle(compteBanque.get().getLibelle())
                    .debit(null)
                    .credit(montant)
                    .libelle(libelle)
                    .build());
        } else {
            log.warn("Impossible de déterminer le type de paiement pour {}", paiement.getId());
            return null;
        }

        EcritureComptable ecriture = EcritureComptable.builder()
                .dateEcriture(paiement.getDate())
                .journal(journal)
                .numeroPiece("PAY-" + paiement.getId().substring(0, Math.min(8, paiement.getId().length())))
                .libelle(libelle)
                .lignes(lignes)
                .pieceJustificativeType("PAIEMENT")
                .pieceJustificativeId(paiement.getId())
                .exerciceId(exercice.getId())
                .lettree(false)
                .pointage(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        EcritureComptable saved = ecritureRepository.save(ecriture);
        updateComptesSoldes(lignes);
        log.info("Écriture générée pour paiement {}", paiement.getId());
        return saved;
    }

    /**
     * Génère une écriture comptable pour une charge payée
     * Débit: compte charge selon catégorie / Crédit: 5141 (Banques) ou 4411 (Fournisseurs)
     */
    @Transactional
    public EcritureComptable genererEcritureCharge(Charge charge) {
        if (!"PAYEE".equals(charge.getStatut()) || charge.getDatePaiement() == null) {
            log.debug("Charge {} non payée, pas d'écriture générée", charge.getId());
            return null;
        }

        // Vérifier si l'écriture existe déjà
        List<EcritureComptable> existing = ecritureRepository.findByPieceJustificativeTypeAndPieceJustificativeId(
                "CHARGE", charge.getId());
        if (!existing.isEmpty()) {
            log.debug("Écriture déjà existante pour charge {}", charge.getId());
            return existing.get(0);
        }

        ExerciceComptable exercice = getExerciceForDate(charge.getDatePaiement());
        if (exercice == null) {
            log.warn("Aucun exercice trouvé pour la date {}", charge.getDatePaiement());
            return null;
        }

        // S'assurer que les comptes essentiels existent
        ensureEssentialAccountsExist();

        // Déterminer le compte de charge selon la catégorie
        String compteChargeCode = getCompteChargeByCategorie(charge.getCategorie());
        Optional<CompteComptable> compteCharge = compteRepository.findByCode(compteChargeCode);
        Optional<CompteComptable> compteBanque = compteRepository.findByCode("5141");

        if (compteCharge.isEmpty() || compteBanque.isEmpty()) {
            log.error("Comptes comptables manquants pour générer l'écriture de charge (compte charge: {}, compte banque: {})", 
                    compteChargeCode, compteBanque.isPresent());
            return null;
        }

        Double montant = charge.getMontant() != null ? charge.getMontant() : 0.0;

        List<LigneEcriture> lignes = new ArrayList<>();
        
        // Débit charge
        lignes.add(LigneEcriture.builder()
                .compteCode(compteCharge.get().getCode())
                .compteLibelle(compteCharge.get().getLibelle())
                .debit(montant)
                .credit(null)
                .libelle(charge.getLibelle())
                .build());

        // Crédit banque
        lignes.add(LigneEcriture.builder()
                .compteCode(compteBanque.get().getCode())
                .compteLibelle(compteBanque.get().getLibelle())
                .debit(null)
                .credit(montant)
                .libelle("Paiement " + charge.getLibelle())
                .build());

        EcritureComptable ecriture = EcritureComptable.builder()
                .dateEcriture(charge.getDatePaiement())
                .journal("OD")
                .numeroPiece("CHG-" + charge.getId().substring(0, Math.min(8, charge.getId().length())))
                .libelle("Charge " + charge.getLibelle())
                .lignes(lignes)
                .pieceJustificativeType("CHARGE")
                .pieceJustificativeId(charge.getId())
                .exerciceId(exercice.getId())
                .lettree(false)
                .pointage(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        EcritureComptable saved = ecritureRepository.save(ecriture);
        updateComptesSoldes(lignes);
        log.info("Écriture générée pour charge {}", charge.getLibelle());
        return saved;
    }

    /**
     * Retourne le code du compte de charge selon la catégorie
     */
    private String getCompteChargeByCategorie(String categorie) {
        if (categorie == null) return "6131"; // Services extérieurs par défaut
        
        return switch (categorie.toUpperCase()) {
            case "LOYER" -> "6132"; // Locations et charges locatives
            case "SALAIRE", "SALAIRES" -> "6171"; // Salaires
            case "TRANSPORT" -> "6136"; // Transports
            case "TELECOMMUNICATION", "TELECOM" -> "6137"; // Télécommunications
            case "EAU", "ELECTRICITE", "ENERGIE" -> "6138"; // Eau, électricité
            case "ASSURANCE" -> "6133"; // Assurances
            case "PUBLICITE", "PUB" -> "6135"; // Publicité
            case "IMPOT", "TAXE" -> "6311"; // Impôts, taxes
            default -> "6131"; // Services extérieurs
        };
    }

    /**
     * Met à jour les soldes des comptes après une écriture
     */
    private void updateComptesSoldes(List<LigneEcriture> lignes) {
        for (LigneEcriture ligne : lignes) {
            Optional<CompteComptable> compte = compteRepository.findByCode(ligne.getCompteCode());
            if (compte.isPresent()) {
                CompteComptable c = compte.get();
                if (ligne.getDebit() != null && ligne.getDebit() > 0) {
                    c.setSoldeDebit((c.getSoldeDebit() != null ? c.getSoldeDebit() : 0.0) + ligne.getDebit());
                }
                if (ligne.getCredit() != null && ligne.getCredit() > 0) {
                    c.setSoldeCredit((c.getSoldeCredit() != null ? c.getSoldeCredit() : 0.0) + ligne.getCredit());
                }
                // Calculer le solde selon le type de compte
                c.setSolde(calculateSolde(c));
                c.setUpdatedAt(LocalDateTime.now());
                compteRepository.save(c);
            }
        }
    }

    /**
     * Calcule le solde d'un compte selon son type
     */
    private Double calculateSolde(CompteComptable compte) {
        Double debit = compte.getSoldeDebit() != null ? compte.getSoldeDebit() : 0.0;
        Double credit = compte.getSoldeCredit() != null ? compte.getSoldeCredit() : 0.0;
        
        // Pour les comptes d'actif et de charge: solde = débit - crédit
        // Pour les comptes de passif et de produit: solde = crédit - débit
        if (compte.getType() == CompteComptable.TypeCompte.ACTIF || 
            compte.getType() == CompteComptable.TypeCompte.CHARGE ||
            compte.getType() == CompteComptable.TypeCompte.TRESORERIE) {
            return debit - credit;
        } else {
            return credit - debit;
        }
    }

    /**
     * Récupère l'exercice comptable pour une date donnée
     */
    private ExerciceComptable getExerciceForDate(LocalDate date) {
        Optional<ExerciceComptable> exercice = exerciceRepository
                .findByDateDebutLessThanEqualAndDateFinGreaterThanEqual(date, date);
        if (exercice.isPresent()) {
            return exercice.get();
        }
        // Si aucun exercice trouvé, créer ou récupérer l'exercice de l'année
        return getOrCreateCurrentExercice();
    }

    /**
     * Crée une écriture comptable manuelle (journal OD) avec validation des totaux.
     */
    @Transactional
    public EcritureComptable createEcritureManuelle(EcritureComptable dto) {
        if (dto.getDateEcriture() == null) {
            throw new IllegalArgumentException("La date de l'écriture est obligatoire");
        }
        if (dto.getLignes() == null || dto.getLignes().isEmpty()) {
            throw new IllegalArgumentException("Au moins une ligne d'écriture est requise");
        }

        double totalDebit = 0.0;
        double totalCredit = 0.0;

        for (LigneEcriture ligne : dto.getLignes()) {
            if (ligne.getCompteCode() == null || ligne.getCompteCode().trim().isEmpty()) {
                throw new IllegalArgumentException("Chaque ligne doit avoir un compte comptable");
            }
            // Vérifier que le compte existe
            if (compteRepository.findByCode(ligne.getCompteCode().trim()).isEmpty()) {
                throw new IllegalArgumentException("Compte inexistant: " + ligne.getCompteCode());
            }
            if (ligne.getDebit() != null && ligne.getDebit() > 0) {
                totalDebit += ligne.getDebit();
            }
            if (ligne.getCredit() != null && ligne.getCredit() > 0) {
                totalCredit += ligne.getCredit();
            }
        }

        if (Math.abs(totalDebit - totalCredit) > 0.01) {
            throw new IllegalArgumentException("L'écriture n'est pas équilibrée (débit != crédit)");
        }

        ExerciceComptable exercice = getExerciceForDate(dto.getDateEcriture());
        LocalDateTime now = LocalDateTime.now();

        EcritureComptable ecriture = EcritureComptable.builder()
                .dateEcriture(dto.getDateEcriture())
                .journal(dto.getJournal() != null && !dto.getJournal().isBlank() ? dto.getJournal() : "OD")
                .numeroPiece(dto.getNumeroPiece())
                .libelle(dto.getLibelle())
                .lignes(dto.getLignes())
                .pieceJustificativeType(dto.getPieceJustificativeType())
                .pieceJustificativeId(dto.getPieceJustificativeId())
                .exerciceId(exercice != null ? exercice.getId() : null)
                .lettree(Boolean.FALSE)
                .pointage(Boolean.FALSE)
                .createdAt(now)
                .updatedAt(now)
                .build();

        EcritureComptable saved = ecritureRepository.save(ecriture);
        updateComptesSoldes(saved.getLignes());
        return saved;
    }

    // ========== MÉTHODES POUR LE CONTROLLER ==========

    /**
     * Récupère toutes les écritures comptables avec filtres optionnels
     */
    public List<EcritureComptable> getEcritures(LocalDate dateDebut, LocalDate dateFin, String journal, String exerciceId, String pieceType, String pieceId) {
        // Priorité: filtrage par pièce justificative (le plus spécifique)
        if (pieceType != null && pieceId != null) {
            List<EcritureComptable> ecritures = ecritureRepository.findByPieceJustificativeTypeAndPieceJustificativeId(pieceType, pieceId);
            // Appliquer les filtres additionnels si nécessaire
            if (dateDebut != null && dateFin != null) {
                ecritures = ecritures.stream()
                        .filter(e -> e.getDateEcriture() != null && 
                                !e.getDateEcriture().isBefore(dateDebut) && 
                                !e.getDateEcriture().isAfter(dateFin))
                        .toList();
            }
            if (journal != null) {
                ecritures = ecritures.stream()
                        .filter(e -> journal.equals(e.getJournal()))
                        .toList();
            }
            if (exerciceId != null) {
                ecritures = ecritures.stream()
                        .filter(e -> exerciceId.equals(e.getExerciceId()))
                        .toList();
            }
            return ecritures;
        }
        
        // Filtres classiques
        if (dateDebut != null && dateFin != null) {
            if (exerciceId != null) {
                return ecritureRepository.findByDateEcritureBetweenAndExerciceId(dateDebut, dateFin, exerciceId);
            }
            return ecritureRepository.findByDateEcritureBetween(dateDebut, dateFin);
        }
        if (journal != null) {
            return ecritureRepository.findByJournal(journal);
        }
        if (exerciceId != null) {
            return ecritureRepository.findByExerciceId(exerciceId);
        }
        return ecritureRepository.findAll();
    }

    /**
     * Récupère une écriture par son ID
     */
    public Optional<EcritureComptable> getEcritureById(String id) {
        return ecritureRepository.findById(id);
    }

    /**
     * Récupère tous les exercices comptables
     */
    public List<ExerciceComptable> getAllExercices() {
        return exerciceRepository.findAll();
    }

    // ========== GRAND LIVRE ==========

    /**
     * Récupère le grand livre pour un compte donné
     */
    public List<EcritureComptable> getGrandLivre(String compteCode, LocalDate dateDebut, LocalDate dateFin, String exerciceId) {
        List<EcritureComptable> ecritures = getEcritures(dateDebut, dateFin, null, exerciceId, null, null);
        return ecritures.stream()
                .filter(e -> e.getLignes() != null && e.getLignes().stream()
                        .anyMatch(l -> compteCode.equals(l.getCompteCode())))
                .toList();
    }

    // ========== BALANCE ==========

    /**
     * Calcule la balance générale (tous les comptes avec débit/crédit/solde)
     */
    public List<CompteComptable> getBalance(LocalDate dateDebut, LocalDate dateFin, String exerciceId) {
        List<EcritureComptable> ecritures = getEcritures(dateDebut, dateFin, null, exerciceId, null, null);
        
        // Réinitialiser les soldes des comptes
        List<CompteComptable> comptes = compteRepository.findAll();
        comptes.forEach(c -> {
            c.setSoldeDebit(0.0);
            c.setSoldeCredit(0.0);
        });

        // Recalculer les soldes depuis les écritures
        for (EcritureComptable ecriture : ecritures) {
            if (ecriture.getLignes() != null) {
                for (LigneEcriture ligne : ecriture.getLignes()) {
                    Optional<CompteComptable> compteOpt = compteRepository.findByCode(ligne.getCompteCode());
                    if (compteOpt.isPresent()) {
                        CompteComptable compte = compteOpt.get();
                        if (ligne.getDebit() != null && ligne.getDebit() > 0) {
                            compte.setSoldeDebit((compte.getSoldeDebit() != null ? compte.getSoldeDebit() : 0.0) + ligne.getDebit());
                        }
                        if (ligne.getCredit() != null && ligne.getCredit() > 0) {
                            compte.setSoldeCredit((compte.getSoldeCredit() != null ? compte.getSoldeCredit() : 0.0) + ligne.getCredit());
                        }
                        compte.setSolde(calculateSolde(compte));
                    }
                }
            }
        }

        return comptes.stream()
                .filter(c -> c.getSoldeDebit() > 0 || c.getSoldeCredit() > 0 || Math.abs(c.getSolde()) > 0.01)
                .sorted((a, b) -> a.getCode().compareTo(b.getCode()))
                .toList();
    }

    // ========== BILAN ==========

    /**
     * Calcule le bilan (Actif et Passif) selon une structure proche du PCGE marocain.
     */
    public Map<String, Object> getBilan(LocalDate date, String exerciceId) {
        List<CompteComptable> balance = getBalance(
                LocalDate.of(date.getYear(), 1, 1),
                date,
                exerciceId
        );

        // Actif immobilisé (classe 2)
        double actifImmobilise = balance.stream()
                .filter(c -> "2".equals(c.getClasse()))
                .mapToDouble(c -> c.getSolde() != null ? c.getSolde() : 0.0)
                .filter(v -> v > 0)
                .sum();

        // Créances (clients et autres comptes de tiers débiteurs – classe 4, type ACTIF)
        double creances = balance.stream()
                .filter(c -> "4".equals(c.getClasse()) && c.getType() == CompteComptable.TypeCompte.ACTIF)
                .mapToDouble(c -> c.getSolde() != null ? c.getSolde() : 0.0)
                .filter(v -> v > 0)
                .sum();

        // Stocks (classe 3)
        double stocks = balance.stream()
                .filter(c -> "3".equals(c.getClasse()))
                .mapToDouble(c -> c.getSolde() != null ? c.getSolde() : 0.0)
                .filter(v -> v > 0)
                .sum();

        // Trésorerie actif (comptes de trésorerie avec solde débiteur positif)
        double tresorerieActif = balance.stream()
                .filter(c -> "5".equals(c.getClasse()) && c.getType() == CompteComptable.TypeCompte.TRESORERIE)
                .mapToDouble(c -> c.getSolde() != null ? c.getSolde() : 0.0)
                .filter(v -> v > 0)
                .sum();

        double actifCirculant = stocks + creances + tresorerieActif;
        double totalActif = actifImmobilise + actifCirculant;

        // Capitaux propres (classe 1)
        double capitauxPropres = balance.stream()
                .filter(c -> "1".equals(c.getClasse()))
                .mapToDouble(c -> c.getSolde() != null ? c.getSolde() : 0.0)
                .filter(v -> v > 0)
                .sum();

        // Dettes financières et d'exploitation (classe 4, type PASSIF)
        double dettes = balance.stream()
                .filter(c -> "4".equals(c.getClasse()) && c.getType() == CompteComptable.TypeCompte.PASSIF)
                .mapToDouble(c -> c.getSolde() != null ? c.getSolde() : 0.0)
                .filter(v -> v > 0)
                .sum();

        // Trésorerie-passif (soldes créditeurs éventuels de comptes de trésorerie)
        double tresoreriePassif = balance.stream()
                .filter(c -> "5".equals(c.getClasse()) && c.getType() == CompteComptable.TypeCompte.TRESORERIE)
                .mapToDouble(c -> c.getSolde() != null ? c.getSolde() : 0.0)
                .filter(v -> v < 0) // découverts ou soldes créditeurs
                .map(v -> Math.abs(v))
                .sum();

        double totalPassif = capitauxPropres + dettes + tresoreriePassif;

        Map<String, Object> bilan = new HashMap<>();
        bilan.put("date", date);
        bilan.put("actifImmobilise", actifImmobilise);
        bilan.put("creances", creances);
        bilan.put("stocks", stocks);
        bilan.put("tresorerieActif", tresorerieActif);
        bilan.put("actifCirculant", actifCirculant);
        bilan.put("totalActif", totalActif);
        bilan.put("capitauxPropres", capitauxPropres);
        bilan.put("dettes", dettes);
        bilan.put("tresoreriePassif", tresoreriePassif);
        bilan.put("totalPassif", totalPassif);
        bilan.put("resultat", totalActif - totalPassif);

        return bilan;
    }

    // ========== CPC (Compte de Produits et Charges) ==========

    /**
     * Calcule le CPC (Compte de Produits et Charges)
     */
    public Map<String, Object> getCPC(LocalDate dateDebut, LocalDate dateFin, String exerciceId) {
        List<CompteComptable> balance = getBalance(dateDebut, dateFin, exerciceId);

        // Produits d'exploitation (classe 7 hors comptes financiers et exceptionnels)
        double produitsExploitation = balance.stream()
                .filter(c -> "7".equals(c.getClasse())
                        && !List.of("7611", "7511").contains(c.getCode()))
                .mapToDouble(c -> c.getSolde() != null ? c.getSolde() : 0.0)
                .filter(v -> v > 0)
                .sum();

        // Charges d'exploitation (classe 6 hors charges financières 6211, impôt 6312, charges exceptionnelles 6711)
        double chargesExploitation = balance.stream()
                .filter(c -> "6".equals(c.getClasse())
                        && !List.of("6211", "6312", "6711").contains(c.getCode()))
                .mapToDouble(c -> c.getSolde() != null ? c.getSolde() : 0.0)
                .filter(v -> v > 0)
                .sum();

        double resultatExploitation = produitsExploitation - chargesExploitation;

        // Produits financiers (ex: 7611)
        double produitsFinanciers = balance.stream()
                .filter(c -> "7611".equals(c.getCode()))
                .mapToDouble(c -> c.getSolde() != null ? c.getSolde() : 0.0)
                .filter(v -> v > 0)
                .sum();

        // Charges financières (ex: 6211)
        double chargesFinancieres = balance.stream()
                .filter(c -> "6211".equals(c.getCode()))
                .mapToDouble(c -> c.getSolde() != null ? c.getSolde() : 0.0)
                .filter(v -> v > 0)
                .sum();

        double resultatFinancier = produitsFinanciers - chargesFinancieres;
        double resultatCourant = resultatExploitation + resultatFinancier;

        // Produits non courants (exceptionnels) – ex: 7511
        double produitsNonCourants = balance.stream()
                .filter(c -> "7511".equals(c.getCode()))
                .mapToDouble(c -> c.getSolde() != null ? c.getSolde() : 0.0)
                .filter(v -> v > 0)
                .sum();

        // Charges non courantes (exceptionnelles) – ex: 6711, 6511
        double chargesNonCourantes = balance.stream()
                .filter(c -> List.of("6711", "6511").contains(c.getCode()))
                .mapToDouble(c -> c.getSolde() != null ? c.getSolde() : 0.0)
                .filter(v -> v > 0)
                .sum();

        double resultatNonCourant = produitsNonCourants - chargesNonCourantes;
        double resultatAvantImpot = resultatCourant + resultatNonCourant;

        // Impôts sur les bénéfices
        double impotBenefices = balance.stream()
                .filter(c -> "6312".equals(c.getCode()))
                .mapToDouble(c -> c.getSolde() != null ? c.getSolde() : 0.0)
                .filter(v -> v > 0)
                .sum();

        double resultatNet = resultatAvantImpot - impotBenefices;

        Map<String, Object> cpc = new HashMap<>();
        cpc.put("dateDebut", dateDebut);
        cpc.put("dateFin", dateFin);
        cpc.put("produitsExploitation", produitsExploitation);
        cpc.put("chargesExploitation", chargesExploitation);
        cpc.put("resultatExploitation", resultatExploitation);
        cpc.put("produitsFinanciers", produitsFinanciers);
        cpc.put("chargesFinancieres", chargesFinancieres);
        cpc.put("resultatFinancier", resultatFinancier);
        cpc.put("resultatCourant", resultatCourant);
        cpc.put("produitsNonCourants", produitsNonCourants);
        cpc.put("chargesNonCourantes", chargesNonCourantes);
        cpc.put("resultatNonCourant", resultatNonCourant);
        cpc.put("resultatAvantImpot", resultatAvantImpot);
        cpc.put("impotBenefices", impotBenefices);
        cpc.put("resultatNet", resultatNet);

        return cpc;
    }

    /**
     * Régénère les écritures comptables pour toutes les factures existantes qui n'en ont pas encore
     */
    @Transactional
    public Map<String, Integer> regenererEcrituresManquantes() {
        int facturesVenteTraitees = 0;
        int facturesAchatTraitees = 0;
        int paiementsTraites = 0;
        int chargesTraitees = 0;
        int erreurs = 0;

        // Récupérer toutes les factures vente
        List<FactureVente> facturesVente = factureVenteRepository.findAll();
        for (FactureVente facture : facturesVente) {
            try {
                // Vérifier si une écriture existe déjà
                List<EcritureComptable> existing = ecritureRepository.findByPieceJustificativeTypeAndPieceJustificativeId(
                        "FACTURE_VENTE", facture.getId());
                if (existing.isEmpty()) {
                    genererEcritureFactureVente(facture);
                    facturesVenteTraitees++;
                    log.info("Écriture générée pour facture vente {}", facture.getNumeroFactureVente());
                }
            } catch (Exception e) {
                log.error("Erreur lors de la génération de l'écriture pour facture vente {}: {}", 
                        facture.getId(), e.getMessage());
                erreurs++;
            }
        }

        // Récupérer toutes les factures achat
        List<FactureAchat> facturesAchat = factureAchatRepository.findAll();
        for (FactureAchat facture : facturesAchat) {
            try {
                String pieceType = facture.getEstAvoir() != null && facture.getEstAvoir() ? "AVOIR_ACHAT" : "FACTURE_ACHAT";
                List<EcritureComptable> existing = ecritureRepository.findByPieceJustificativeTypeAndPieceJustificativeId(
                        pieceType, facture.getId());
                if (existing.isEmpty()) {
                    if (Boolean.TRUE.equals(facture.getEstAvoir())) {
                        genererEcritureAvoirAchat(facture);
                    } else {
                        genererEcritureFactureAchat(facture);
                    }
                    facturesAchatTraitees++;
                    log.info("Écriture générée pour {} {}", pieceType, facture.getNumeroFactureAchat());
                }
            } catch (Exception e) {
                log.error("Erreur lors de la génération de l'écriture pour facture achat {}: {}", 
                        facture.getId(), e.getMessage());
                erreurs++;
            }
        }

        // Récupérer tous les paiements
        List<Paiement> paiements = paiementRepository.findAll();
        for (Paiement paiement : paiements) {
            try {
                List<EcritureComptable> existing = ecritureRepository.findByPieceJustificativeTypeAndPieceJustificativeId(
                        "PAIEMENT", paiement.getId());
                if (existing.isEmpty()) {
                    genererEcriturePaiement(paiement);
                    paiementsTraites++;
                    log.info("Écriture générée pour paiement {}", paiement.getId());
                }
            } catch (Exception e) {
                log.error("Erreur lors de la génération de l'écriture pour paiement {}: {}", 
                        paiement.getId(), e.getMessage());
                erreurs++;
            }
        }

        // Récupérer toutes les charges
        List<Charge> charges = chargeRepository.findAll();
        for (Charge charge : charges) {
            try {
                List<EcritureComptable> existing = ecritureRepository.findByPieceJustificativeTypeAndPieceJustificativeId(
                        "CHARGE", charge.getId());
                if (existing.isEmpty()) {
                    genererEcritureCharge(charge);
                    chargesTraitees++;
                    log.info("Écriture générée pour charge {}", charge.getId());
                }
            } catch (Exception e) {
                log.error("Erreur lors de la génération de l'écriture pour charge {}: {}", 
                        charge.getId(), e.getMessage());
                erreurs++;
            }
        }

        Map<String, Integer> result = new HashMap<>();
        result.put("facturesVenteTraitees", facturesVenteTraitees);
        result.put("facturesAchatTraitees", facturesAchatTraitees);
        result.put("paiementsTraites", paiementsTraites);
        result.put("chargesTraitees", chargesTraitees);
        result.put("erreurs", erreurs);
        result.put("total", facturesVenteTraitees + facturesAchatTraitees + paiementsTraites + chargesTraitees);

        log.info("Régénération des écritures terminée: {} factures vente, {} factures achat, {} erreurs", 
                facturesVenteTraitees, facturesAchatTraitees, erreurs);

        return result;
    }
}

