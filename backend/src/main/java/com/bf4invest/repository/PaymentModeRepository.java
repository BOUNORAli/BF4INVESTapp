package com.bf4invest.repository;

import com.bf4invest.model.PaymentMode;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentModeRepository extends MongoRepository<PaymentMode, String> {
    List<PaymentMode> findByActiveTrueOrderByNameAsc();
    List<PaymentMode> findAllByOrderByNameAsc();
}

