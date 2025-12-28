package com.bf4invest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteDataResponse {
    private Map<String, Long> deletedCounts;
    private long totalDeleted;
    private List<String> errors;
}

