package com.bf4invest.controller;

import com.bf4invest.model.BandeCommande;
import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.FactureVente;
import com.bf4invest.model.OrdreVirement;
import com.bf4invest.pdf.PdfService;
import com.bf4invest.service.BandeCommandeService;
import com.bf4invest.service.FactureAchatService;
import com.bf4invest.service.FactureVenteService;
import com.bf4invest.service.OrdreVirementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pdf")
@RequiredArgsConstructor
public class PdfController {
    
    private final PdfService pdfService;
    private final BandeCommandeService bcService;
    private final FactureVenteService factureVenteService;
    private final FactureAchatService factureAchatService;
    private final OrdreVirementService ordreVirementService;
    
    @GetMapping("/bandes-commandes/{id}")
    public ResponseEntity<byte[]> generateBCPdf(@PathVariable String id) {
        try {
            BandeCommande bc = bcService.findById(id)
                    .orElseThrow(() -> new RuntimeException("BC not found"));
            
            byte[] pdfBytes = pdfService.generateBC(bc);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "BC-" + bc.getNumeroBC() + ".pdf");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/factures-ventes/{id}")
    public ResponseEntity<byte[]> generateFactureVentePdf(@PathVariable String id) {
        try {
            FactureVente facture = factureVenteService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Facture not found"));
            
            byte[] pdfBytes = pdfService.generateFactureVente(facture);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "FV-" + facture.getNumeroFactureVente() + ".pdf");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/factures-achats/{id}")
    public ResponseEntity<byte[]> generateFactureAchatPdf(@PathVariable String id) {
        try {
            FactureAchat facture = factureAchatService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Facture not found"));
            
            byte[] pdfBytes = pdfService.generateFactureAchat(facture);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "FA-" + facture.getNumeroFactureAchat() + ".pdf");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/factures-ventes/{id}/bon-de-livraison")
    public ResponseEntity<byte[]> generateBonDeLivraisonPdf(@PathVariable String id) {
        try {
            FactureVente facture = factureVenteService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Facture not found"));
            
            byte[] pdfBytes = pdfService.generateBonDeLivraison(facture);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            String blNum = facture.getNumeroFactureVente() != null ? 
                "BL-" + facture.getNumeroFactureVente() : "BL-" + id;
            headers.setContentDispositionFormData("attachment", blNum + ".pdf");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/ordres-virement/{id}")
    public ResponseEntity<byte[]> generateOrdreVirementPdf(@PathVariable String id) {
        try {
            OrdreVirement ov = ordreVirementService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Ordre de virement not found"));
            
            byte[] pdfBytes = pdfService.generateOrdreVirement(ov);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            String fileName = ov.getNumeroOV() != null ? 
                "OV-" + ov.getNumeroOV() + ".pdf" : "OV-" + id + ".pdf";
            headers.setContentDispositionFormData("attachment", fileName);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}




