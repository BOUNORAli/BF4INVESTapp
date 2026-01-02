package com.bf4invest.service;

import com.bf4invest.model.Product;
import com.bf4invest.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository productRepository;
    private final AuditService auditService;
    
    public List<Product> findAll() {
        return productRepository.findAll();
    }
    
    public Optional<Product> findById(String id) {
        return productRepository.findById(id);
    }
    
    public Product create(Product product) {
        // Initialiser le stock à 0 si non fourni
        if (product.getQuantiteEnStock() == null) {
            product.setQuantiteEnStock(0);
        }
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        Product saved = productRepository.save(product);
        
        // Log d'audit
        auditService.logCreate("Produit", saved.getId(), 
            "Produit " + (saved.getDesignation() != null ? saved.getDesignation() : saved.getRefArticle()) + " créé");
        
        return saved;
    }
    
    public Product update(String id, Product product) {
        return productRepository.findById(id)
                .map(existing -> {
                    String oldName = existing.getDesignation() != null ? existing.getDesignation() : existing.getRefArticle();
                    
                    existing.setRefArticle(product.getRefArticle());
                    existing.setDesignation(product.getDesignation());
                    existing.setUnite(product.getUnite());
                    existing.setPrixAchatUnitaireHT(product.getPrixAchatUnitaireHT());
                    existing.setPrixVenteUnitaireHT(product.getPrixVenteUnitaireHT());
                    existing.setTva(product.getTva());
                    existing.setFournisseurId(product.getFournisseurId());
                    if (product.getQuantiteEnStock() != null) {
                        existing.setQuantiteEnStock(product.getQuantiteEnStock());
                    }
                    // Mettre à jour l'image si fournie
                    if (product.getImageBase64() != null) {
                        existing.setImageBase64(product.getImageBase64());
                        existing.setImageContentType(product.getImageContentType());
                    }
                    existing.setUpdatedAt(LocalDateTime.now());
                    Product saved = productRepository.save(existing);
                    
                    // Log d'audit
                    String newName = saved.getDesignation() != null ? saved.getDesignation() : saved.getRefArticle();
                    auditService.logUpdate("Produit", id, oldName, newName);
                    
                    return saved;
                })
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
    }
    
    public void delete(String id) {
        productRepository.findById(id).ifPresent(product -> {
            String productName = product.getDesignation() != null ? product.getDesignation() : product.getRefArticle();
            productRepository.deleteById(id);
            
            // Log d'audit
            auditService.logDelete("Produit", id, "Produit " + productName + " supprimé");
        });
    }
    
    /**
     * Met à jour le stock d'un produit en ajoutant ou soustrayant une quantité
     * @param productId ID du produit
     * @param quantityChange Quantité à ajouter (positive) ou soustraire (négative)
     * @return Le produit mis à jour
     */
    public Product updateStock(String productId, Integer quantityChange) {
        return productRepository.findById(productId)
                .map(product -> {
                    Integer currentStock = product.getQuantiteEnStock() != null ? product.getQuantiteEnStock() : 0;
                    product.setQuantiteEnStock(currentStock + quantityChange);
                    product.setUpdatedAt(LocalDateTime.now());
                    return productRepository.save(product);
                })
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
    }
    
    /**
     * Met à jour le stock d'un produit par sa référence article
     * @param refArticle Référence article du produit
     * @param quantityChange Quantité à ajouter (positive) ou soustraire (négative)
     * @return Le produit mis à jour, ou null si le produit n'existe pas
     */
    public Product updateStockByRef(String refArticle, Integer quantityChange) {
        return productRepository.findByRefArticle(refArticle)
                .map(product -> {
                    Integer currentStock = product.getQuantiteEnStock() != null ? product.getQuantiteEnStock() : 0;
                    product.setQuantiteEnStock(currentStock + quantityChange);
                    product.setUpdatedAt(LocalDateTime.now());
                    return productRepository.save(product);
                })
                .orElse(null); // Retourne null si le produit n'existe pas (ne bloque pas la facture)
    }
    
    /**
     * Récupère le stock d'un produit
     * @param productId ID du produit
     * @return Le stock actuel (0 si null)
     */
    public Integer getStock(String productId) {
        return productRepository.findById(productId)
                .map(product -> product.getQuantiteEnStock() != null ? product.getQuantiteEnStock() : 0)
                .orElse(0);
    }
    
    /**
     * Récupère le stock d'un produit par sa référence article
     * @param refArticle Référence article du produit
     * @return Le stock actuel (0 si le produit n'existe pas ou si null)
     */
    public Integer getStockByRef(String refArticle) {
        return productRepository.findByRefArticle(refArticle)
                .map(product -> product.getQuantiteEnStock() != null ? product.getQuantiteEnStock() : 0)
                .orElse(0);
    }
}




