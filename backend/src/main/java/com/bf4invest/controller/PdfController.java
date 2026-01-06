package com.bf4invest.controller;

import com.bf4invest.model.BandeCommande;
import com.bf4invest.model.FactureAchat;
import com.bf4invest.model.FactureVente;
import com.bf4invest.model.OrdreVirement;
import com.bf4invest.pdf.PdfService;
import com.bf4invest.service.BandeCommandeService;
import com.bf4invest.service.ComptabiliteService;
import com.bf4invest.service.FactureAchatService;
import com.bf4invest.service.FactureVenteService;
import com.bf4invest.service.OrdreVirementService;
import com.bf4invest.service.TVAService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/pdf")
@RequiredArgsConstructor
public class PdfController {
    
    private final PdfService pdfService;
    private final BandeCommandeService bcService;
    private final FactureVenteService factureVenteService;
    private final FactureAchatService factureAchatService;
    private final OrdreVirementService ordreVirementService;
    private final ComptabiliteService comptabiliteService;
    private final TVAService tvaService;
    
    @GetMapping("/bandes-commandes/{id}")
    public ResponseEntity<byte[]> generateBCPdf(@PathVariable String id) {
        try {
            BandeCommande bc = bcService.findById(id)
                    .orElseThrow(() -> new RuntimeException("BC not found with id: " + id));
            
            log.debug("Found BC: id={}, numeroBC={}, hasLignesAchat={}, hasLignes={}", 
                id, bc.getNumeroBC(), 
                bc.getLignesAchat() != null && !bc.getLignesAchat().isEmpty(),
                bc.getLignes() != null && !bc.getLignes().isEmpty());
            
            // Validation: vérifier que le BC a au moins des lignes
            if ((bc.getLignesAchat() == null || bc.getLignesAchat().isEmpty()) && 
                (bc.getLignes() == null || bc.getLignes().isEmpty())) {
                log.warn("BC {} has no product lines (lignesAchat={}, lignes={}), cannot generate PDF", 
                    id, bc.getLignesAchat(), bc.getLignes());
                return ResponseEntity.badRequest().build();
            }
            
            byte[] pdfBytes = pdfService.generateBC(bc);
            
            if (pdfBytes == null || pdfBytes.length == 0) {
                log.error("PDF generation returned empty bytes for BC {}", id);
                return ResponseEntity.internalServerError().build();
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            String fileName = bc.getNumeroBC() != null ? "BC-" + bc.getNumeroBC() + ".pdf" : "BC-" + id + ".pdf";
            headers.setContentDispositionFormData("attachment", fileName);
            
            log.debug("Successfully generated PDF for BC {}: {} bytes", id, pdfBytes.length);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (RuntimeException e) {
            log.error("Error generating BC PDF for id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(404).build();
        } catch (Exception e) {
            log.error("Unexpected error generating BC PDF for id {}: {}", id, e.getMessage(), e);
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

    // ========== EXPORTS PDF COMPTABLES ==========

    @GetMapping("/comptabilite/journal")
    public ResponseEntity<byte[]> generateJournalComptablePdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String exerciceId,
            @RequestParam(required = false) String pieceType,
            @RequestParam(required = false) String pieceId
    ) {
        try {
            if (dateDebut == null) dateDebut = LocalDate.now().withDayOfMonth(1);
            if (dateFin == null) dateFin = LocalDate.now();
            
            var ecritures = comptabiliteService.getEcritures(dateDebut, dateFin, null, exerciceId, pieceType, pieceId);
            byte[] pdfBytes = pdfService.generateJournalComptable(ecritures, dateDebut, dateFin);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            String fileName = pieceType != null && pieceId != null ? 
                "journal_" + pieceType + "_" + pieceId + ".pdf" : 
                "journal_comptable_" + dateDebut.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf";
            headers.setContentDispositionFormData("attachment", fileName);
            
            return ResponseEntity.ok().headers(headers).body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/comptabilite/grand-livre")
    public ResponseEntity<byte[]> generateGrandLivrePdf(
            @RequestParam String compteCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String exerciceId
    ) {
        try {
            if (dateDebut == null) dateDebut = LocalDate.now().withDayOfMonth(1);
            if (dateFin == null) dateFin = LocalDate.now();
            
            var ecritures = comptabiliteService.getGrandLivre(compteCode, dateDebut, dateFin, exerciceId);
            var compte = comptabiliteService.getCompteByCode(compteCode);
            String compteLibelle = compte.map(c -> c.getLibelle()).orElse("Compte " + compteCode);
            
            byte[] pdfBytes = pdfService.generateGrandLivre(ecritures, compteCode, compteLibelle, dateDebut, dateFin);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", 
                "grand_livre_" + compteCode + "_" + dateDebut.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf");
            
            return ResponseEntity.ok().headers(headers).body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/comptabilite/balance")
    public ResponseEntity<byte[]> generateBalancePdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String exerciceId
    ) {
        try {
            if (dateDebut == null) dateDebut = LocalDate.now().withDayOfMonth(1);
            if (dateFin == null) dateFin = LocalDate.now();
            
            var balance = comptabiliteService.getBalance(dateDebut, dateFin, exerciceId);
            byte[] pdfBytes = pdfService.generateBalance(balance, dateDebut, dateFin);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", 
                "balance_" + dateDebut.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf");
            
            return ResponseEntity.ok().headers(headers).body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/comptabilite/bilan")
    public ResponseEntity<byte[]> generateBilanPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String exerciceId
    ) {
        try {
            var bilan = comptabiliteService.getBilan(date, exerciceId);
            byte[] pdfBytes = pdfService.generateBilanPdf(bilan);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", 
                "bilan_" + date.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf");
            
            return ResponseEntity.ok().headers(headers).body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/comptabilite/cpc")
    public ResponseEntity<byte[]> generateCpcPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String exerciceId
    ) {
        try {
            var cpc = comptabiliteService.getCPC(dateDebut, dateFin, exerciceId);
            byte[] pdfBytes = pdfService.generateCpcPdf(cpc);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", 
                "cpc_" + dateDebut.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf");
            
            return ResponseEntity.ok().headers(headers).body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/tva/declaration/{mois}/{annee}")
    public ResponseEntity<byte[]> generateDeclarationTVAPdf(
            @PathVariable Integer mois,
            @PathVariable Integer annee
    ) {
        try {
            var declaration = tvaService.getDeclarationByMoisAnnee(mois, annee)
                    .orElseThrow(() -> new RuntimeException("Déclaration TVA non trouvée"));
            
            byte[] pdfBytes = pdfService.generateDeclarationTVAPDF(declaration);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", 
                "declaration_tva_" + String.format("%02d", mois) + "_" + annee + ".pdf");
            
            return ResponseEntity.ok().headers(headers).body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}




