package com.bf4invest.repository;

import com.bf4invest.model.DeclarationTVA;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeclarationTVARepository extends MongoRepository<DeclarationTVA, String> {
    Optional<DeclarationTVA> findByMoisAndAnnee(Integer mois, Integer annee);
    List<DeclarationTVA> findByAnneeOrderByMoisDesc(Integer annee);
    List<DeclarationTVA> findByStatut(DeclarationTVA.StatutDeclaration statut);
}

