package com.bf4invest.repository;

import com.bf4invest.model.CompanyInfo;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyInfoRepository extends MongoRepository<CompanyInfo, String> {

    /**
     * Récupère la dernière version des informations société (il ne devrait y avoir qu'un document).
     */
    Optional<CompanyInfo> findFirstByOrderByUpdatedAtDesc();
}


