package com.bf4invest.controller;

import com.bf4invest.model.CompanyInfo;
import com.bf4invest.service.CompanyInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/company-info")
@RequiredArgsConstructor
public class CompanyInfoController {

    private final CompanyInfoService companyInfoService;

    @GetMapping
    public ResponseEntity<CompanyInfo> getCompanyInfo() {
        CompanyInfo info = companyInfoService.getCompanyInfo();
        return ResponseEntity.ok(info);
    }

    @PutMapping
    public ResponseEntity<CompanyInfo> updateCompanyInfo(@RequestBody CompanyInfo info) {
        CompanyInfo updated = companyInfoService.saveCompanyInfo(info);
        return ResponseEntity.status(HttpStatus.OK).body(updated);
    }
}





