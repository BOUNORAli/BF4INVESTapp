package com.bf4invest.repository;

import com.bf4invest.model.ImportLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportLogRepository extends MongoRepository<ImportLog, String> {
    List<ImportLog> findAllByOrderByCreatedAtDesc();
}

