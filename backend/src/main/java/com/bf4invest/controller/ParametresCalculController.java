package com.bf4invest.controller;

import com.bf4invest.model.ParametresCalcul;
import com.bf4invest.service.ParametresCalculService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/parametres-calcul")
@RequiredArgsConstructor
public class ParametresCalculController {
    
    private final ParametresCalculService parametresCalculService;
    
    @GetMapping
    public ResponseEntity<ParametresCalcul> getParametres() {
        ParametresCalcul parametres = parametresCalculService.getParametres();
        return ResponseEntity.ok(parametres);
    }
    
    @PutMapping
    public ResponseEntity<ParametresCalcul> updateParametres(@RequestBody ParametresCalcul parametres) {
        ParametresCalcul updated = parametresCalculService.updateParametres(parametres);
        return ResponseEntity.ok(updated);
    }
    
    @PostMapping
    public ResponseEntity<ParametresCalcul> saveParametres(@RequestBody ParametresCalcul parametres) {
        ParametresCalcul saved = parametresCalculService.saveParametres(parametres);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}




