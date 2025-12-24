package com.bf4invest.repository;

import com.bf4invest.model.Charge;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChargeRepository extends MongoRepository<Charge, String> {
}


