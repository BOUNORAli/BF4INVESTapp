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

        Cloudinary client = buildClient();

        Map<String, Object> params = ObjectUtils.asMap(
                "folder", resolveFolder(kind),
                "public_id", UUID.randomUUID().toString(),
                "resource_type", "auto",
                "overwrite", true
        );

        Map uploadResult = client.uploader().upload(file.getBytes(), params);

        String publicId = (String) uploadResult.get("public_id");
        String secureUrl = (String) uploadResult.get("secure_url");
        String format = (String) uploadResult.get("format");
        Number bytes = (Number) uploadResult.get("bytes");

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
    }

    public boolean delete(String publicId) {
        try {
            Cloudinary client = buildClient();
            Map res = client.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "auto"));
            return "ok".equals(res.get("result"));
        } catch (Exception e) {
            log.error("Erreur suppression Cloudinary", e);
            return false;
        }
    }

    public String generateUrl(String publicId) {
        try {
            Cloudinary client = buildClient();
            return client.url()
                    .secure(true)
                    .resourceType("auto")
                    .generate(publicId);
        } catch (Exception e) {
            log.error("Erreur génération URL Cloudinary", e);
            return null;
        }
    }
}

