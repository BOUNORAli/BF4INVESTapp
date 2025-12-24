package com.bf4invest.repository;

import com.bf4invest.model.CompteComptable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompteComptableRepository extends MongoRepository<CompteComptable, String> {
    Optional<CompteComptable> findByCode(String code);
    List<CompteComptable> findByClasse(String classe);
    List<CompteComptable> findByType(CompteComptable.TypeCompte type);
    List<CompteComptable> findByActifTrue();
}

