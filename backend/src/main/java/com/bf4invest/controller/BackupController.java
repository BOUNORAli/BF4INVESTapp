package com.bf4invest.controller;

import com.bf4invest.excel.ExcelExportService;
import com.bf4invest.model.BandeCommande;
import com.bf4invest.model.Charge;
import com.bf4invest.model.Client;
import com.bf4invest.model.OperationComptable;
import com.bf4invest.model.Product;
import com.bf4invest.model.Supplier;
import com.bf4invest.model.TransactionBancaire;
import com.bf4invest.repository.BandeCommandeRepository;
import com.bf4invest.repository.ChargeRepository;
import com.bf4invest.repository.ClientRepository;
import com.bf4invest.repository.OperationComptableRepository;
import com.bf4invest.repository.ProductRepository;
import com.bf4invest.repository.SupplierRepository;
import com.bf4invest.repository.TransactionBancaireRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/backup")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class BackupController {

    private final ExcelExportService excelExportService;
    private final ProductRepository productRepository;
    private final BandeCommandeRepository bandeCommandeRepository;
    private final OperationComptableRepository operationComptableRepository;
    private final TransactionBancaireRepository transactionBancaireRepository;
    private final ClientRepository clientRepository;
    private final SupplierRepository supplierRepository;
    private final ChargeRepository chargeRepository;

    @GetMapping("/produits")
    public ResponseEntity<byte[]> exportProduits() {
        try {
            List<Product> products = productRepository.findAll();
            byte[] excelBytes = excelExportService.exportProductsToImportFormat(products);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "Catalogue_Produits.xlsx");

            return ResponseEntity.ok().headers(headers).body(excelBytes);
        } catch (Exception e) {
            log.error("Erreur lors de l'export du catalogue produits pour sauvegarde", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/bandes-commandes")
    public ResponseEntity<byte[]> exportBandesCommandes() {
        try {
            List<BandeCommande> bcs = bandeCommandeRepository.findAll();
            byte[] excelBytes = excelExportService.exportBCsToImportFormat(bcs);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "Historique_BC.xlsx");

            return ResponseEntity.ok().headers(headers).body(excelBytes);
        } catch (Exception e) {
            log.error("Erreur lors de l'export des bandes commandes pour sauvegarde", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/operations")
    public ResponseEntity<byte[]> exportOperations() {
        try {
            List<OperationComptable> operations = operationComptableRepository.findAll();
            byte[] excelBytes = excelExportService.exportOperationsToImportFormat(operations);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "Operations_Comptables.xlsx");

            return ResponseEntity.ok().headers(headers).body(excelBytes);
        } catch (Exception e) {
            log.error("Erreur lors de l'export des opérations comptables pour sauvegarde", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/releve-bancaire")
    public ResponseEntity<byte[]> exportReleveBancaire() {
        try {
            List<TransactionBancaire> transactions = transactionBancaireRepository.findAll();
            byte[] excelBytes = excelExportService.exportReleveBancaireToImportFormat(transactions);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "Releve_Bancaire.xlsx");

            return ResponseEntity.ok().headers(headers).body(excelBytes);
        } catch (Exception e) {
            log.error("Erreur lors de l'export du relevé bancaire pour sauvegarde", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/complet")
    public ResponseEntity<ByteArrayResource> exportBackupComplet() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            // Fichiers réimportables
            addEntry(zos, "Catalogue_Produits.xlsx",
                    excelExportService.exportProductsToImportFormat(productRepository.findAll()));
            addEntry(zos, "Historique_BC.xlsx",
                    excelExportService.exportBCsToImportFormat(bandeCommandeRepository.findAll()));
            addEntry(zos, "Operations_Comptables.xlsx",
                    excelExportService.exportOperationsToImportFormat(operationComptableRepository.findAll()));
            addEntry(zos, "Releve_Bancaire.xlsx",
                    excelExportService.exportReleveBancaireToImportFormat(transactionBancaireRepository.findAll()));

            // Fichiers de référence
            addEntry(zos, "Clients.xlsx",
                    excelExportService.exportClientsToExcel(clientRepository.findAll()));
            addEntry(zos, "Fournisseurs.xlsx",
                    excelExportService.exportSuppliersToExcel(supplierRepository.findAll()));
            addEntry(zos, "Charges.xlsx",
                    excelExportService.exportChargesToExcel(chargeRepository.findAll()));

            zos.finish();

            byte[] zipBytes = baos.toByteArray();

            String dateStr = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            String fileName = "Sauvegarde_BF4Invest_" + dateStr + ".zip";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(zipBytes.length)
                    .body(new ByteArrayResource(zipBytes));
        } catch (Exception e) {
            log.error("Erreur lors de la génération de la sauvegarde complète ZIP", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private void addEntry(ZipOutputStream zos, String entryName, byte[] content) throws Exception {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        zos.write(content);
        zos.closeEntry();
    }
}

