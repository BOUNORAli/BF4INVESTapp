package com.bf4invest.controller;

import com.bf4invest.dto.SupabaseFileResult;
import com.bf4invest.model.FactureAchat;
import com.bf4invest.repository.FactureAchatRepository;
import com.bf4invest.service.CloudinaryStorageService;
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

    private final CloudinaryStorageService cloudinaryStorageService;
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
        log.info("📥 [BACKEND] Upload reçu - Nom: {}, Taille: {} bytes, ContentType: {}, FactureId: {}", 
                file.getOriginalFilename(), file.getSize(), file.getContentType(), factureId);
        
        try {
            if (file.isEmpty()) {
                log.warn("⚠️ [BACKEND] Fichier vide rejeté");
                return ResponseEntity.badRequest().body(Map.of("error", "Fichier vide"));
            }
            
            String contentType = file.getContentType();
            log.info("🔍 [BACKEND] Validation contentType: {}", contentType);
            boolean isAllowed = isAllowedContentType(contentType);
            log.info("🔍 [BACKEND] ContentType autorisé: {}", isAllowed);
            
            if (!isAllowed) {
                log.warn("⚠️ [BACKEND] ContentType non autorisé rejeté: {}", contentType);
                return ResponseEntity.badRequest().body(Map.of("error", "Formats acceptés: images ou PDF"));
            }
            
            if (file.getSize() > 10 * 1024 * 1024) {
                log.warn("⚠️ [BACKEND] Fichier trop volumineux: {} bytes", file.getSize());
                return ResponseEntity.badRequest().body(Map.of("error", "Taille max 10MB dépassée"));
            }

            log.info("✅ [BACKEND] Validation OK, appel CloudinaryStorageService.upload");
            SupabaseFileResult result = cloudinaryStorageService.upload(file, "facture-achat");
            log.info("✅ Upload réussi - FileId: {}, Filename: {}, ContentType: {}, URL: {}", 
                    result.getFileId(), result.getFilename(), result.getContentType(), result.getSignedUrl());

            if (StringUtils.isNotBlank(factureId)) {
                Optional<FactureAchat> factureOpt = factureAchatRepository.findById(factureId);
                if (factureOpt.isPresent()) {
                    FactureAchat facture = factureOpt.get();
                    facture.setFichierFactureId(result.getFileId());
                    facture.setFichierFactureNom(result.getFilename());
                    facture.setFichierFactureType(result.getContentType());
                    facture.setFichierFactureUrl(result.getSignedUrl());
                    FactureAchat saved = factureAchatRepository.save(facture);
                    log.info("💾 Facture mise à jour - ID: {}, FileId: {}, Filename: {}", 
                            saved.getId(), saved.getFichierFactureId(), saved.getFichierFactureNom());
                } else {
                    log.warn("⚠️ Facture non trouvée pour ID: {}", factureId);
                }
            } else {
                log.info("ℹ️ Aucun factureId fourni, fichier uploadé mais non associé à une facture");
            }

            return ResponseEntity.ok(Map.of(
                    "fileId", result.getFileId(),
                    "filename", result.getFilename(),
                    "contentType", result.getContentType(),
                    "signedUrl", result.getSignedUrl()
            ));
        } catch (IllegalStateException e) {
            log.error("❌ [BACKEND] IllegalStateException lors de l'upload: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.error("❌ [BACKEND] IllegalArgumentException lors de l'upload: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("❌ [BACKEND] IOException lors de l'upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Erreur lors de l'upload: " + e.getMessage()));
        } catch (Exception e) {
            log.error("❌ [BACKEND] Exception inattendue lors de l'upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Erreur inattendue lors de l'upload: " + e.getMessage()));
        }
    }

    @GetMapping("/url")
    public ResponseEntity<Map<String, String>> getFileUrl(
            @RequestParam("fileId") String fileId,
            @RequestParam(value = "contentType", required = false) String contentType
    ) {
        log.info("🔗 Génération URL pour fileId: {}, contentType: {}", fileId, contentType);
        
        // Si contentType n'est pas fourni, essayer de le récupérer depuis la facture
        if (contentType == null) {
            Optional<FactureAchat> factureOpt = factureAchatRepository.findByFichierFactureId(fileId);
            if (factureOpt.isPresent()) {
                contentType = factureOpt.get().getFichierFactureType();
                log.info("🔍 ContentType récupéré depuis la facture: {}", contentType);
            }
        }
        
        String url = cloudinaryStorageService.generateUrl(fileId, contentType);
        if (url == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Impossible de générer l'URL"));
        }
        return ResponseEntity.ok(Map.of("fileId", fileId, "url", url));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteFile(
            @RequestParam("fileId") String fileId,
            @RequestParam(value = "factureId", required = false) String factureId
    ) {
        log.info("🗑️ Suppression fichier: {}, factureId: {}", fileId, factureId);
        boolean deleted = cloudinaryStorageService.delete(fileId);

        if (deleted && StringUtils.isNotBlank(factureId)) {
            Optional<FactureAchat> factureOpt = factureAchatRepository.findById(factureId);
            factureOpt.ifPresent(f -> {
                if (fileId.equals(f.getFichierFactureId())) {
                    f.setFichierFactureId(null);
                    f.setFichierFactureNom(null);
                    f.setFichierFactureType(null);
                    f.setFichierFactureUrl(null);
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

