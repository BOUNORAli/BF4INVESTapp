package com.bf4invest.repository;

import com.bf4invest.model.EcritureComptable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface EcritureComptableRepository extends MongoRepository<EcritureComptable, String> {
    List<EcritureComptable> findByDateEcritureBetween(LocalDate dateDebut, LocalDate dateFin);
    List<EcritureComptable> findByJournal(String journal);
    List<EcritureComptable> findByExerciceId(String exerciceId);
    List<EcritureComptable> findByPieceJustificativeTypeAndPieceJustificativeId(String type, String id);
    List<EcritureComptable> findByDateEcritureBetweenAndExerciceId(LocalDate dateDebut, LocalDate dateFin, String exerciceId);
}

