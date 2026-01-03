package com.bf4invest.repository;

import com.bf4invest.model.Client;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends MongoRepository<Client, String> {
    Optional<Client> findByIce(String ice);
    boolean existsByIce(String ice);
    Optional<Client> findByNomIgnoreCase(String nom);
}




