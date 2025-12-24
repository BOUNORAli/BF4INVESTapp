package com.bf4invest.repository;

import com.bf4invest.model.ExerciceComptable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExerciceComptableRepository extends MongoRepository<ExerciceComptable, String> {
    Optional<ExerciceComptable> findByCode(String code);
    Optional<ExerciceComptable> findByDateDebutLessThanEqualAndDateFinGreaterThanEqual(LocalDate date1, LocalDate date2);
    List<ExerciceComptable> findByStatut(ExerciceComptable.StatutExercice statut);
    Optional<ExerciceComptable> findFirstByStatutOrderByDateDebutDesc(ExerciceComptable.StatutExercice statut);
}

