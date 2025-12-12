package com.bf4invest.controller;

import com.bf4invest.dto.PaymentModeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SettingsController {
    
    @GetMapping("/payment-modes")
    public ResponseEntity<List<PaymentModeDto>> getPaymentModes() {
        List<PaymentModeDto> modes = new ArrayList<>();
        modes.add(PaymentModeDto.builder().id("pm1").name("Virement Bancaire").active(true).build());
        modes.add(PaymentModeDto.builder().id("pm2").name("Chèque").active(true).build());
        modes.add(PaymentModeDto.builder().id("pm3").name("Espèces").active(true).build());
        modes.add(PaymentModeDto.builder().id("pm4").name("LCN (Lettre de Change)").active(true).build());
        modes.add(PaymentModeDto.builder().id("pm5").name("Compensation").active(true).build());
        return ResponseEntity.ok(modes);
    }
}




