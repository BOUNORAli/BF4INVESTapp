package com.bf4invest.controller;

import com.bf4invest.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {
    
    private final FileStorageService fileStorageService;
    
    /**
     * Upload un fichier
     * POST /files/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "entityId", required = false) String entityId,
            @RequestParam(value = "entityType", required = false) String entityType
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Le fichier ne peut pas être vide"));
        }
        
        // Vérifier le type de fichier (images ou PDF)
        String contentType = file.getContentType();
        if (contentType == null || 
            (!contentType.startsWith("image/") && !contentType.equals("application/pdf"))) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Seuls les fichiers images et PDF sont autorisés"));
        }
        
        try {
            // Préparer les métadonnées
            Map<String, String> metadata = new HashMap<>();
            if (type != null) {
                metadata.put("type", type); // "facture_achat", "releve_bancaire", etc.
            }
            if (entityId != null) {
                metadata.put("entityId", entityId);
            }
            if (entityType != null) {
                metadata.put("entityType", entityType);
            }
            
            // Upload le fichier
            String fileId = fileStorageService.uploadFile(file, metadata);
            
            Map<String, Object> response = new HashMap<>();
            response.put("fileId", fileId);
            response.put("filename", file.getOriginalFilename());
            response.put("contentType", contentType);
            response.put("size", file.getSize());
            response.put("message", "Fichier uploadé avec succès");
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (IOException e) {
            log.error("Erreur lors de l'upload du fichier", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur lors de l'upload: " + e.getMessage()));
        }
    }
    
    /**
     * Télécharger un fichier
     * GET /files/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable String id) {
        try {
            var fileOpt = fileStorageService.getFile(id);
            if (fileOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            var file = fileOpt.get();
            var inputStream = fileStorageService.getFileContent(id);
            
            // Déterminer le Content-Type depuis les métadonnées
            String contentType = "application/octet-stream";
            if (file.getMetadata() != null) {
                Object contentTypeObj = file.getMetadata().get("contentType");
                if (contentTypeObj != null) {
                    contentType = contentTypeObj.toString();
                }
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentDispositionFormData("attachment", file.getFilename());
            headers.setContentLength(file.getLength());
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(inputStream));
                    
        } catch (IOException e) {
            log.error("Erreur lors du téléchargement du fichier: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtenir les métadonnées d'un fichier
     * GET /files/{id}/metadata
     */
    @GetMapping("/{id}/metadata")
    public ResponseEntity<Map<String, Object>> getFileMetadata(@PathVariable String id) {
        try {
            Map<String, Object> metadata = fileStorageService.getFileMetadata(id);
            return ResponseEntity.ok(metadata);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Supprimer un fichier
     * DELETE /files/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteFile(@PathVariable String id) {
        boolean deleted = fileStorageService.deleteFile(id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Fichier supprimé avec succès"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Fichier non trouvé"));
        }
    }
    
    /**
     * Vérifier si un fichier existe
     * GET /files/{id}/exists
     */
    @GetMapping("/{id}/exists")
    public ResponseEntity<Map<String, Boolean>> fileExists(@PathVariable String id) {
        boolean exists = fileStorageService.fileExists(id);
        return ResponseEntity.ok(Map.of("exists", exists));
    }
}

