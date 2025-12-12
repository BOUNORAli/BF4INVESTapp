package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "import_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportLog {
    @Id
    private String id;
    
    private String fileName;
    private String details;
    private boolean success;
    private int successCount;
    private int errorCount;
    private LocalDateTime createdAt;
}

