package com.bf4invest.repository;

import com.bf4invest.model.SoldeGlobal;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SoldeGlobalRepository extends MongoRepository<SoldeGlobal, String> {
}


