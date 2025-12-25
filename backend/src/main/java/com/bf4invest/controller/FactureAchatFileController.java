package com.bf4invest.controller;

import com.bf4invest.dto.SupabaseFileResult;
import com.bf4invest.model.FactureAchat;
import com.bf4invest.repository.FactureAchatRepository;
import com.bf4invest.service.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/factures-achats/files")
@RequiredArgsConstructor
@Slf4j
public class FactureAchatFileController {

    private final SupabaseStorageService supabaseStorageService;
    private final FactureAchatRepository factureAchatRepository;

    private boolean isAllowedContentType(String contentType) {
        if (contentType == null) return false;
        return contentType.startsWith("image/") || contentType.equalsIgnoreCase(MediaType.APPLICATION_PDF_VALUE);
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "factureId", required = false) String factureId
    ) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Fichier vide"));
            }
            if (!isAllowedContentType(file.getContentType())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Formats acceptés: images ou PDF"));
            }
            if (file.getSize() > 10 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(Map.of("error", "Taille max 10MB dépassée"));
            }

            SupabaseFileResult result = supabaseStorageService.upload(file, "facture-achat");

            if (StringUtils.isNotBlank(factureId)) {
                factureAchatRepository.findById(factureId).ifPresent(facture -> {
                    facture.setFichierFactureId(result.getFileId());
                    facture.setFichierFactureNom(result.getFilename());
                    facture.setFichierFactureType(result.getContentType());
                    factureAchatRepository.save(facture);
                });
            }

            return ResponseEntity.ok(Map.of(
                    "fileId", result.getFileId(),
                    "filename", result.getFilename(),
                    "contentType", result.getContentType(),
                    "signedUrl", result.getSignedUrl()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("Erreur upload Supabase", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Erreur lors de l'upload"));
        }
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String fileId) {
        return supabaseStorageService.download(fileId);
    }

    @GetMapping("/{fileId}/signed-url")
    public ResponseEntity<Map<String, String>> getSignedUrl(@PathVariable String fileId) {
        String url = supabaseStorageService.generateSignedUrl(fileId, 3600);
        if (url == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Impossible de générer l'URL signée"));
        }
        return ResponseEntity.ok(Map.of("signedUrl", url));
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Map<String, String>> deleteFile(
            @PathVariable String fileId,
            @RequestParam(value = "factureId", required = false) String factureId
    ) {
        boolean deleted = supabaseStorageService.delete(fileId);

        if (deleted && StringUtils.isNotBlank(factureId)) {
            Optional<FactureAchat> factureOpt = factureAchatRepository.findById(factureId);
            factureOpt.ifPresent(f -> {
                if (fileId.equals(f.getFichierFactureId())) {
                    f.setFichierFactureId(null);
                    f.setFichierFactureNom(null);
                    f.setFichierFactureType(null);
                    factureAchatRepository.save(f);
                }
            });
        }

        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Fichier supprimé"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Fichier non trouvé"));
        }
    }
}

