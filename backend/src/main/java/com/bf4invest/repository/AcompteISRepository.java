package com.bf4invest.repository;

import com.bf4invest.model.AcompteIS;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AcompteISRepository extends MongoRepository<AcompteIS, String> {
    List<AcompteIS> findByAnneeOrderByTrimestreAsc(Integer annee);
    Optional<AcompteIS> findByAnneeAndTrimestre(Integer annee, Integer trimestre);
}
