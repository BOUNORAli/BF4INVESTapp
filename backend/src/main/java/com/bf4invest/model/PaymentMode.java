package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "payment_modes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMode {
    @Id
    private String id;
    
    private String name;
    private boolean active;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}




