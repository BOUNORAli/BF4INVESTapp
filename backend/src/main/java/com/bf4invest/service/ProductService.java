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
    
    public List<Product> findAll() {
        return productRepository.findAll();
    }
    
    public Optional<Product> findById(String id) {
        return productRepository.findById(id);
    }
    
    public Product create(Product product) {
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }
    
    public Product update(String id, Product product) {
        return productRepository.findById(id)
                .map(existing -> {
                    existing.setRefArticle(product.getRefArticle());
                    existing.setDesignation(product.getDesignation());
                    existing.setUnite(product.getUnite());
                    existing.setPrixAchatUnitaireHT(product.getPrixAchatUnitaireHT());
                    existing.setPrixVenteUnitaireHT(product.getPrixVenteUnitaireHT());
                    existing.setTva(product.getTva());
                    existing.setFournisseurId(product.getFournisseurId());
                    existing.setUpdatedAt(LocalDateTime.now());
                    return productRepository.save(existing);
                })
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
    }
    
    public void delete(String id) {
        productRepository.deleteById(id);
    }
}




