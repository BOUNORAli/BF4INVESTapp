package com.bf4invest.controller;

import com.bf4invest.dto.SupabaseFileResult;
import com.bf4invest.model.ReleveBancaireFichier;
import com.bf4invest.repository.ReleveBancaireFichierRepository;
import com.bf4invest.service.CloudinaryStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/releve-bancaire/files")
@RequiredArgsConstructor
@Slf4j
public class ReleveBancaireFileController {

    private final CloudinaryStorageService cloudinaryStorageService;
    private final ReleveBancaireFichierRepository releveRepo;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam Integer mois,
            @RequestParam Integer annee
    ) {
        try {
            log.info("📤 Upload relevé bancaire - Mois: {}, Année: {}, Taille: {} bytes", mois, annee, file.getSize());
            
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Fichier vide"));
            }
            if (!"application/pdf".equalsIgnoreCase(file.getContentType())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Seuls les fichiers PDF sont acceptés"));
            }
            if (file.getSize() > 10 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(Map.of("error", "Taille max 10MB dépassée"));
            }

            SupabaseFileResult result = cloudinaryStorageService.upload(file, "releve");
            log.info("✅ Upload Cloudinary réussi - FileId: {}, URL: {}", result.getFileId(), result.getSignedUrl());

            ReleveBancaireFichier saved = releveRepo.save(
                    ReleveBancaireFichier.builder()
                            .fichierId(result.getFileId())
                            .nomFichier(StringUtils.defaultIfBlank(result.getFilename(), file.getOriginalFilename()))
                            .contentType(file.getContentType())
                            .taille(file.getSize())
                            .url(result.getSignedUrl())
                            .mois(mois)
                            .annee(annee)
                            .uploadedAt(LocalDateTime.now())
                            .build()
            );
            log.info("✅ Métadonnées sauvegardées - ID: {}", saved.getId());

            return ResponseEntity.ok(Map.of(
                    "id", saved.getId(),
                    "fileId", result.getFileId(),
                    "filename", saved.getNomFichier(),
                    "signedUrl", result.getSignedUrl()
            ));
        } catch (IllegalStateException | IllegalArgumentException e) {
            log.error("❌ Erreur validation/configuration: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("❌ Erreur upload Cloudinary releve", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Erreur lors de l'upload: " + e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Erreur inattendue upload relevé", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Erreur inattendue: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<ReleveBancaireFichier>> list(
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) Integer annee
    ) {
        List<ReleveBancaireFichier> files;
        if (mois != null && annee != null) {
            files = releveRepo.findByMoisAndAnnee(mois, annee);
        } else {
            files = releveRepo.findAll();
        }
        return ResponseEntity.ok(files);
    }

    @GetMapping("/url")
    public ResponseEntity<Map<String, String>> getFileUrl(@RequestParam("fileId") String fileId) {
        log.info("🔗 Génération URL pour fileId: {}", fileId);
        // Les relevés bancaires sont toujours des PDFs
        String url = cloudinaryStorageService.generateUrl(fileId, "application/pdf");
        if (url == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Impossible de générer l'URL"));
        }
        return ResponseEntity.ok(Map.of("fileId", fileId, "url", url));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> delete(
            @RequestParam("fileId") String fileId
    ) {
        log.info("🗑️ Suppression fichier: {}", fileId);
        // Les relevés bancaires sont toujours des PDFs
        boolean deleted = cloudinaryStorageService.delete(fileId, "application/pdf");
        if (deleted) {
            releveRepo.findAll().stream()
                    .filter(r -> fileId.equals(r.getFichierId()))
                    .forEach(r -> {
                        releveRepo.deleteById(r.getId());
                    });
            return ResponseEntity.ok(Map.of("message", "Fichier supprimé"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Fichier non trouvé"));
        }
    }
}

