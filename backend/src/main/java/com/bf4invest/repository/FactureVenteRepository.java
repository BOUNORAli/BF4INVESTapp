package com.bf4invest.repository;

import com.bf4invest.model.FactureVente;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FactureVenteRepository extends MongoRepository<FactureVente, String> {
    Optional<FactureVente> findByNumeroFactureVente(String numero);
    List<FactureVente> findByClientId(String clientId);
    List<FactureVente> findByEtatPaiement(String etat);
    List<FactureVente> findByDateEcheanceLessThanEqual(LocalDate date);
    List<FactureVente> findByBandeCommandeId(String bandeCommandeId);
}




