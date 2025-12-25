package com.bf4invest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupabaseFileResult {
    private String fileId;
    private String filename;
    private String contentType;
    private long size;
    private String signedUrl;
}

