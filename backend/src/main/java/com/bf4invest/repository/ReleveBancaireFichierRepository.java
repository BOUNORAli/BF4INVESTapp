package com.bf4invest.repository;

import com.bf4invest.model.ReleveBancaireFichier;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReleveBancaireFichierRepository extends MongoRepository<ReleveBancaireFichier, String> {
    
    /**
     * Trouve tous les fichiers de relevé bancaire pour un mois et une année donnés
     */
    List<ReleveBancaireFichier> findByMoisAndAnnee(Integer mois, Integer annee);
    
    /**
     * Trouve un fichier par son ID GridFS
     */
    Optional<ReleveBancaireFichier> findByFichierId(String fichierId);
    
    /**
     * Trouve tous les fichiers pour une année donnée
     */
    List<ReleveBancaireFichier> findByAnnee(Integer annee);
    
    /**
     * Supprime un fichier par son ID GridFS
     */
    void deleteByFichierId(String fichierId);
}

