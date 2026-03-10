package com.bf4invest.repository;

import com.bf4invest.model.DeclarationIS;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeclarationISRepository extends MongoRepository<DeclarationIS, String> {
    Optional<DeclarationIS> findByAnnee(Integer annee);
    List<DeclarationIS> findAllByOrderByAnneeDesc();
}
