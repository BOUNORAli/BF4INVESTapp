package com.bf4invest.repository;

import com.bf4invest.model.OperationComptable;
import com.bf4invest.model.TypeOperation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OperationComptableRepository extends MongoRepository<OperationComptable, String> {
    List<OperationComptable> findByNumeroBc(String numeroBc);
    List<OperationComptable> findByTypeOperation(TypeOperation typeOperation);
    List<OperationComptable> findByDateOperationBetween(LocalDate dateDebut, LocalDate dateFin);
    List<OperationComptable> findByContrePartie(String contrePartie);
    Optional<OperationComptable> findByNumeroFactureAndTypeMouvement(String numeroFacture, com.bf4invest.model.TypeMouvement typeMouvement);
    List<OperationComptable> findByAnneeAndMois(Integer annee, Integer mois);
}

