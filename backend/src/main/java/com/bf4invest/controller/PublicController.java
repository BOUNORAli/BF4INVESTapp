package com.bf4invest.controller;

import com.bf4invest.dto.PublicProductDto;
import com.bf4invest.model.Product;
import com.bf4invest.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Endpoints publics consommés par le site vitrine (sans authentification).
 */
@Slf4j
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController {

    private final ProductRepository productRepository;

    /**
     * Retourne la liste des produits disponibles pour le site vitrine.
     * Aucune information sensible (prix d'achat, fournisseur, etc.) n'est exposée.
     */
    @GetMapping("/produits")
    public ResponseEntity<List<PublicProductDto>> getPublicProducts() {
        List<Product> products = productRepository.findAll();

        List<PublicProductDto> dtos = products.stream()
                .map(p -> PublicProductDto.builder()
                        .id(p.getId())
                        .refArticle(p.getRefArticle())
                        .designation(p.getDesignation())
                        .unite(p.getUnite())
                        .prixVentePondereHT(p.getPrixVentePondereHT())
                        .tva(p.getTva())
                        .quantiteEnStock(p.getQuantiteEnStock())
                        .imageBase64(p.getImageBase64())
                        .imageContentType(p.getImageContentType())
                        .build())
                .collect(Collectors.toList());

        log.debug("PublicController.getPublicProducts - {} produits retournés pour le site vitrine", dtos.size());
        return ResponseEntity.ok(dtos);
    }
}

