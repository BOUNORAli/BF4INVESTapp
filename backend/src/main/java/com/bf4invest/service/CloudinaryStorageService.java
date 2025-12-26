package com.bf4invest.service;

import com.bf4invest.dto.SupabaseFileResult;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryStorageService {

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    @Value("${cloudinary.folder.factures:bf4/factures}")
    private String facturesFolder;

    @Value("${cloudinary.folder.releves:bf4/releves}")
    private String relevesFolder;

    private Cloudinary buildClient() {
        if (StringUtils.isAnyBlank(cloudName, apiKey, apiSecret)) {
            throw new IllegalStateException("Configuration Cloudinary manquante (cloud name / api key / api secret)");
        }
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }

    private String resolveFolder(String kind) {
        if ("releve".equalsIgnoreCase(kind)) {
            return relevesFolder;
        }
        return facturesFolder;
    }

    public SupabaseFileResult upload(MultipartFile file, String kind) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier ne peut pas être vide");
        }
        String contentType = StringUtils.defaultIfBlank(file.getContentType(), MediaType.APPLICATION_OCTET_STREAM_VALUE);
        if (!(contentType.startsWith("image/") || MediaType.APPLICATION_PDF_VALUE.equals(contentType))) {
            throw new IllegalArgumentException("Formats acceptés: images ou PDF");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Taille maximale 10MB dépassée");
        }

        try {
            Cloudinary client = buildClient();
            log.info("🔧 Configuration Cloudinary - Cloud: {}, Folder: {}", cloudName, resolveFolder(kind));

            // Déterminer le type de ressource selon le type de fichier
            String resourceType = "auto";
            if (MediaType.APPLICATION_PDF_VALUE.equals(contentType)) {
                resourceType = "raw"; // PDFs doivent être en "raw"
            }
            
            Map<String, Object> params = ObjectUtils.asMap(
                    "folder", resolveFolder(kind),
                    "public_id", UUID.randomUUID().toString(),
                    "resource_type", resourceType,
                    "overwrite", true
            );

            log.info("📤 Upload vers Cloudinary - Taille: {} bytes, ContentType: {}", file.getSize(), contentType);
            Map uploadResult = client.uploader().upload(file.getBytes(), params);
            log.info("✅ Upload Cloudinary réussi - Result: {}", uploadResult);

            String publicId = (String) uploadResult.get("public_id");
            String secureUrl = (String) uploadResult.get("secure_url");
            String format = (String) uploadResult.get("format");
            Number bytes = (Number) uploadResult.get("bytes");

            if (StringUtils.isBlank(publicId)) {
                throw new IllegalStateException("Cloudinary n'a pas retourné de public_id");
            }
            if (StringUtils.isBlank(secureUrl)) {
                throw new IllegalStateException("Cloudinary n'a pas retourné d'URL sécurisée");
            }

            String filename = file.getOriginalFilename();
            if (StringUtils.isBlank(filename) && uploadResult.containsKey("original_filename")) {
                filename = uploadResult.get("original_filename").toString();
            }

            return SupabaseFileResult.builder()
                    .fileId(publicId)
                    .filename(filename)
                    .contentType(contentType)
                    .size(bytes != null ? bytes.longValue() : file.getSize())
                    .signedUrl(secureUrl)
                    .build();
        } catch (IllegalStateException e) {
            log.error("❌ Erreur configuration Cloudinary: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("❌ Erreur upload Cloudinary", e);
            throw new IOException("Erreur lors de l'upload vers Cloudinary: " + e.getMessage(), e);
        }
    }

    public boolean delete(String publicId) {
        return delete(publicId, null);
    }
    
    public boolean delete(String publicId, String contentType) {
        try {
            Cloudinary client = buildClient();
            
            // Déterminer le resource_type selon le contentType
            String resourceType = "auto";
            if (contentType != null && MediaType.APPLICATION_PDF_VALUE.equals(contentType)) {
                resourceType = "raw";
            } else if (contentType != null && contentType.startsWith("image/")) {
                resourceType = "image";
            }
            
            // Essayer avec le resource_type déterminé
            try {
                Map res = client.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", resourceType));
                if ("ok".equals(res.get("result"))) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("Suppression avec resource_type {} échouée, essai avec auto", resourceType, e);
            }
            
            // Si ça échoue, essayer avec "auto"
            Map res = client.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "auto"));
            return "ok".equals(res.get("result"));
        } catch (Exception e) {
            log.error("Erreur suppression Cloudinary", e);
            return false;
        }
    }

    public String generateUrl(String publicId) {
        return generateUrl(publicId, null);
    }
    
    public String generateUrl(String publicId, String contentType) {
        String resourceType = "auto"; // Par défaut
        
        try {
            Cloudinary client = buildClient();
            
            // Déterminer le resource_type selon le contentType
            if (contentType != null && MediaType.APPLICATION_PDF_VALUE.equals(contentType)) {
                resourceType = "raw"; // PDFs sont en "raw"
            } else if (contentType != null && contentType.startsWith("image/")) {
                resourceType = "image"; // Images sont en "image"
            } else {
                // Si pas de contentType ou type inconnu, utiliser "auto" qui détecte automatiquement
                resourceType = "auto";
            }
            
            String url = client.url()
                    .secure(true)
                    .resourceType(resourceType)
                    .generate(publicId);
            log.info("🔗 URL générée pour publicId: {} (resourceType: {}, contentType: {}) -> {}", publicId, resourceType, contentType, url);
            return url;
        } catch (Exception e) {
            log.warn("Tentative avec resource_type {} échouée, essai avec auto", resourceType, e);
            try {
                Cloudinary client = buildClient();
                String url = client.url()
                        .secure(true)
                        .resourceType("auto")
                        .generate(publicId);
                log.info("🔗 URL générée (auto) pour publicId: {} -> {}", publicId, url);
                return url;
            } catch (Exception e2) {
                log.error("Erreur génération URL Cloudinary", e2);
                return null;
            }
        }
    }
    
    /**
     * Génère une URL signée pour téléchargement direct avec transformation
     */
    public String generateSignedDownloadUrl(String publicId) {
        try {
            Cloudinary client = buildClient();
            // URL signée avec transformation pour forcer le téléchargement
            String url = client.url()
                    .secure(true)
                    .resourceType("auto")
                    .transformation(new com.cloudinary.Transformation<>()
                            .flags("attachment")) // Force le téléchargement
                    .generate(publicId);
            log.info("🔗 URL téléchargement générée pour publicId: {} -> {}", publicId, url);
            return url;
        } catch (Exception e) {
            log.error("Erreur génération URL signée Cloudinary", e);
            return null;
        }
    }
}

