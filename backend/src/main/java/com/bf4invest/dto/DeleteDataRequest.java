package com.bf4invest.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteDataRequest {
    private List<String> collections;
    private String confirmation;
}

