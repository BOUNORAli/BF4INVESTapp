package com.bf4invest.repository;

import com.bf4invest.model.Supplier;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierRepository extends MongoRepository<Supplier, String> {
    Optional<Supplier> findByIce(String ice);
    Optional<Supplier> findByNom(String nom);
}




