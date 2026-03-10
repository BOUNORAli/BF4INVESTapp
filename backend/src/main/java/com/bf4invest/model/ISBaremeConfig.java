package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "is_bareme_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ISBaremeConfig {
    @Id
    private String id;

    private Double seuilTaux1;
    private Double seuilTaux2;
    private Double taux1;
    private Double taux2;
    private Double taux3;
    private Double cotisationMinimaleTaux;
    private Double cotisationMinimaleMinimum;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
