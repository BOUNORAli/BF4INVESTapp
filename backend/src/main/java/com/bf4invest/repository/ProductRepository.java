package com.bf4invest.repository;

import com.bf4invest.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {
    Optional<Product> findByRefArticle(String refArticle);
    List<Product> findByFournisseurId(String fournisseurId);
}




