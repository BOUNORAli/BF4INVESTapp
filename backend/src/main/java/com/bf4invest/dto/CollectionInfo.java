package com.bf4invest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionInfo {
    private String name;
    private String description;
    private String category;
    private boolean critical; // Collections critiques nécessitant une attention particulière
    private long count; // Nombre d'éléments dans la collection
}

