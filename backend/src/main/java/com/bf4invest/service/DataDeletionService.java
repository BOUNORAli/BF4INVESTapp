package com.bf4invest.service;

import com.bf4invest.dto.CollectionInfo;
import com.bf4invest.dto.DeleteDataResponse;
import com.bf4invest.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataDeletionService {
    
    // Repositories pour les données métier
    private final ClientRepository clientRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final BandeCommandeRepository bandeCommandeRepository;
    private final FactureAchatRepository factureAchatRepository;
    private final FactureVenteRepository factureVenteRepository;
    private final PaiementRepository paiementRepository;
    private final ChargeRepository chargeRepository;
    private final OrdreVirementRepository ordreVirementRepository;
    
    // Repositories pour la comptabilité
    private final OperationComptableRepository operationComptableRepository;
    private final EcritureComptableRepository ecritureComptableRepository;
    private final DeclarationTVARepository declarationTVARepository;
    private final ExerciceComptableRepository exerciceComptableRepository;
    private final CompteComptableRepository compteComptableRepository;
    
    // Repositories pour les données bancaires
    private final ReleveBancaireFichierRepository releveBancaireFichierRepository;
    private final TransactionBancaireRepository transactionBancaireRepository;
    
    // Repositories pour l'historique
    private final HistoriqueSoldeRepository historiqueSoldeRepository;
    private final ImportLogRepository importLogRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationRepository notificationRepository;
    
    // Repositories pour la configuration
    private final UserRepository userRepository;
    private final PaymentModeRepository paymentModeRepository;
    private final CompanyInfoRepository companyInfoRepository;
    private final ParametresCalculRepository parametresCalculRepository;
    private final SoldeGlobalRepository soldeGlobalRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    
    /**
     * Retourne la liste de toutes les collections disponibles avec leurs descriptions
     */
    public List<CollectionInfo> getAvailableCollections() {
        Map<String, MongoRepository<?, ?>> repositoryMap = buildRepositoryMap();
        List<CollectionInfo> collections = new ArrayList<>();
        
        // Données Métier
        addCollectionInfo(collections, "clients", "Clients", "Données Métier", false, clientRepository);
        addCollectionInfo(collections, "fournisseurs", "Fournisseurs", "Données Métier", false, supplierRepository);
        addCollectionInfo(collections, "produits", "Produits", "Données Métier", false, productRepository);
        addCollectionInfo(collections, "bandes_commandes", "Bandes de Commande", "Données Métier", false, bandeCommandeRepository);
        addCollectionInfo(collections, "factures_achats", "Factures d'Achat", "Données Métier", false, factureAchatRepository);
        addCollectionInfo(collections, "factures_ventes", "Factures de Vente", "Données Métier", false, factureVenteRepository);
        addCollectionInfo(collections, "paiements", "Paiements", "Données Métier", false, paiementRepository);
        addCollectionInfo(collections, "charges", "Charges", "Données Métier", false, chargeRepository);
        addCollectionInfo(collections, "ordres_virement", "Ordres de Virement", "Données Métier", false, ordreVirementRepository);
        
        // Comptabilité
        addCollectionInfo(collections, "operations_comptables", "Opérations Comptables", "Comptabilité", false, operationComptableRepository);
        addCollectionInfo(collections, "ecritures_comptables", "Écritures Comptables", "Comptabilité", false, ecritureComptableRepository);
        addCollectionInfo(collections, "declarations_tva", "Déclarations TVA", "Comptabilité", false, declarationTVARepository);
        addCollectionInfo(collections, "exercices_comptables", "Exercices Comptables", "Comptabilité", false, exerciceComptableRepository);
        addCollectionInfo(collections, "comptes_comptables", "Comptes Comptables", "Comptabilité", false, compteComptableRepository);
        
        // Bancaire
        addCollectionInfo(collections, "releves_bancaires_fichiers", "Relevés Bancaires", "Bancaire", false, releveBancaireFichierRepository);
        addCollectionInfo(collections, "transactions_bancaires", "Transactions Bancaires", "Bancaire", false, transactionBancaireRepository);
        
        // Historique
        addCollectionInfo(collections, "historique_solde", "Historique des Soldes", "Historique", false, historiqueSoldeRepository);
        addCollectionInfo(collections, "import_logs", "Logs d'Import", "Historique", false, importLogRepository);
        addCollectionInfo(collections, "audit_logs", "Logs d'Audit", "Historique", false, auditLogRepository);
        addCollectionInfo(collections, "notifications", "Notifications", "Historique", false, notificationRepository);
        
        // Configuration (critiques)
        addCollectionInfo(collections, "users", "Utilisateurs", "Configuration", true, userRepository);
        addCollectionInfo(collections, "payment_modes", "Modes de Paiement", "Configuration", true, paymentModeRepository);
        addCollectionInfo(collections, "company_info", "Informations de l'Entreprise", "Configuration", true, companyInfoRepository);
        addCollectionInfo(collections, "parametres_calcul", "Paramètres de Calcul", "Configuration", true, parametresCalculRepository);
        addCollectionInfo(collections, "solde_global", "Solde Global", "Configuration", true, soldeGlobalRepository);
        addCollectionInfo(collections, "refresh_tokens", "Tokens de Rafraîchissement", "Configuration", false, refreshTokenRepository);
        
        return collections;
    }
    
    private void addCollectionInfo(List<CollectionInfo> collections, String name, String description, 
                                   String category, boolean critical, MongoRepository<?, ?> repository) {
        long count = repository.count();
        collections.add(CollectionInfo.builder()
                .name(name)
                .description(description)
                .category(category)
                .critical(critical)
                .count(count)
                .build());
    }
    
    /**
     * Construit une map des noms de collections vers leurs repositories
     */
    private Map<String, MongoRepository<?, ?>> buildRepositoryMap() {
        Map<String, MongoRepository<?, ?>> map = new HashMap<>();
        
        // Données Métier
        map.put("clients", clientRepository);
        map.put("fournisseurs", supplierRepository);
        map.put("produits", productRepository);
        map.put("bandes_commandes", bandeCommandeRepository);
        map.put("factures_achats", factureAchatRepository);
        map.put("factures_ventes", factureVenteRepository);
        map.put("paiements", paiementRepository);
        map.put("charges", chargeRepository);
        map.put("ordres_virement", ordreVirementRepository);
        
        // Comptabilité
        map.put("operations_comptables", operationComptableRepository);
        map.put("ecritures_comptables", ecritureComptableRepository);
        map.put("declarations_tva", declarationTVARepository);
        map.put("exercices_comptables", exerciceComptableRepository);
        map.put("comptes_comptables", compteComptableRepository);
        
        // Bancaire
        map.put("releves_bancaires_fichiers", releveBancaireFichierRepository);
        map.put("transactions_bancaires", transactionBancaireRepository);
        
        // Historique
        map.put("historique_solde", historiqueSoldeRepository);
        map.put("import_logs", importLogRepository);
        map.put("audit_logs", auditLogRepository);
        map.put("notifications", notificationRepository);
        
        // Configuration
        map.put("users", userRepository);
        map.put("payment_modes", paymentModeRepository);
        map.put("company_info", companyInfoRepository);
        map.put("parametres_calcul", parametresCalculRepository);
        map.put("solde_global", soldeGlobalRepository);
        map.put("refresh_tokens", refreshTokenRepository);
        
        return map;
    }
    
    /**
     * Supprime toutes les données des collections spécifiées
     */
    @Transactional
    public DeleteDataResponse deleteAllData(List<String> collections) {
        Map<String, MongoRepository<?, ?>> repositoryMap = buildRepositoryMap();
        Map<String, Long> deletedCounts = new HashMap<>();
        List<String> errors = new ArrayList<>();
        long totalDeleted = 0;
        
        // Récupérer l'utilisateur actuel pour le logging
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "unknown";
        
        log.warn("Début de la suppression de données par l'utilisateur: {}", username);
        log.warn("Collections à supprimer: {}", collections);
        
        for (String collectionName : collections) {
            MongoRepository<?, ?> repository = repositoryMap.get(collectionName);
            
            if (repository == null) {
                String error = "Collection inconnue: " + collectionName;
                errors.add(error);
                log.error(error);
                continue;
            }
            
            try {
                long countBefore = repository.count();
                repository.deleteAll();
                long countAfter = repository.count();
                long deleted = countBefore - countAfter;
                
                deletedCounts.put(collectionName, deleted);
                totalDeleted += deleted;
                
                log.warn("Collection '{}' supprimée: {} éléments supprimés", collectionName, deleted);
            } catch (Exception e) {
                String error = "Erreur lors de la suppression de la collection '" + collectionName + "': " + e.getMessage();
                errors.add(error);
                log.error(error, e);
            }
        }
        
        log.warn("Suppression terminée. Total d'éléments supprimés: {}", totalDeleted);
        if (!errors.isEmpty()) {
            log.error("Erreurs rencontrées: {}", errors);
        }
        
        return DeleteDataResponse.builder()
                .deletedCounts(deletedCounts)
                .totalDeleted(totalDeleted)
                .errors(errors)
                .build();
    }
}

