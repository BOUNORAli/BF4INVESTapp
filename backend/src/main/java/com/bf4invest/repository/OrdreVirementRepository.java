package com.bf4invest.repository;

import com.bf4invest.model.OrdreVirement;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrdreVirementRepository extends MongoRepository<OrdreVirement, String> {
    Optional<OrdreVirement> findByNumeroOV(String numeroOV);
    List<OrdreVirement> findByBeneficiaireId(String beneficiaireId);
    List<OrdreVirement> findByStatut(String statut);
    List<OrdreVirement> findByDateOVBetween(LocalDate dateDebut, LocalDate dateFin);
    List<OrdreVirement> findByDateExecution(LocalDate dateExecution);
}

