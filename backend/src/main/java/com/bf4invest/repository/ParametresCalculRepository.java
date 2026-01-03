package com.bf4invest.repository;

import com.bf4invest.model.ParametresCalcul;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParametresCalculRepository extends MongoRepository<ParametresCalcul, String> {
    /**
     * Récupère les paramètres de calcul (il ne devrait y avoir qu'un seul document)
     */
    Optional<ParametresCalcul> findFirstByOrderByUpdatedAtDesc();
}




