package com.bf4invest.repository;

import com.bf4invest.model.HistoriqueSolde;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HistoriqueSoldeRepository extends MongoRepository<HistoriqueSolde, String> {
    List<HistoriqueSolde> findByPartenaireIdAndPartenaireTypeOrderByDateDesc(String partenaireId, String partenaireType);
    List<HistoriqueSolde> findByDateBetweenOrderByDateDesc(LocalDateTime start, LocalDateTime end);
    List<HistoriqueSolde> findByTypeOrderByDateDesc(String type);
    List<HistoriqueSolde> findAllByOrderByDateDesc();
}


