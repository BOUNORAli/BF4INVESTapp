package com.bf4invest.repository;

import com.bf4invest.model.ISBaremeConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ISBaremeConfigRepository extends MongoRepository<ISBaremeConfig, String> {
    Optional<ISBaremeConfig> findFirstByOrderByUpdatedAtDesc();
}
