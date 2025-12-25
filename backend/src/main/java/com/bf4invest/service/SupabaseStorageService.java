package com.bf4invest.service;

import com.bf4invest.dto.SupabaseFileResult;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupabaseStorageService {

    @Value("${supabase.url:}")
    private String supabaseUrl;

    @Value("${supabase.service-key:}")
    private String supabaseServiceKey;

    @Value("${supabase.bucket.factures:factures-achat}")
    private String bucketName;

    private WebClient getClient() {
        if (StringUtils.isBlank(supabaseUrl) || StringUtils.isBlank(supabaseServiceKey)) {
            throw new IllegalStateException("Configuration Supabase manquante (SUPABASE_URL ou SUPABASE_SERVICE_KEY)");
        }
        return WebClient.builder()
                .baseUrl(supabaseUrl + "/storage/v1")
                .defaultHeader("apikey", supabaseServiceKey)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + supabaseServiceKey)
                .build();
    }

    private String sanitizeFilename(String originalFilename) {
        String base = StringUtils.defaultIfBlank(originalFilename, "fichier");
        base = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (base.length() > 120) {
            base = base.substring(base.length() - 120);
        }
        return base;
    }

    public SupabaseFileResult upload(MultipartFile file, String prefix) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier ne peut pas être vide");
        }

        String safeName = sanitizeFilename(file.getOriginalFilename());
        String objectPath = (prefix != null && !prefix.isBlank() ? prefix + "-" : "") +
                UUID.randomUUID() + "-" + safeName;

        WebClient client = getClient();

        client.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/object/{bucket}/{path}")
                        .build(bucketName, objectPath))
                .contentType(MediaType.parseMediaType(StringUtils.defaultIfBlank(file.getContentType(), MediaType.APPLICATION_OCTET_STREAM_VALUE)))
                .header("x-upsert", "true")
                .bodyValue(new InputStreamResource(file.getInputStream()))
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofSeconds(30));

        String signedUrl = generateSignedUrl(objectPath, 3600);

        return SupabaseFileResult.builder()
                .fileId(objectPath)
                .filename(safeName)
                .contentType(StringUtils.defaultIfBlank(file.getContentType(), MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .size(file.getSize())
                .signedUrl(signedUrl)
                .build();
    }

    public ResponseEntity<byte[]> download(String objectPath) {
        try {
            WebClient client = getClient();
            byte[] content = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/object/{bucket}/{path}")
                            .build(bucketName, objectPath))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block(Duration.ofSeconds(30));

            if (content == null) {
                return ResponseEntity.notFound().build();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentLength(content.length);
            headers.setContentDispositionFormData("attachment", URLEncoder.encode(objectPath, StandardCharsets.UTF_8));

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(content);
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Erreur lors du téléchargement Supabase", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    public boolean delete(String objectPath) {
        try {
            WebClient client = getClient();
            client.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/object/{bucket}/{path}")
                            .build(bucketName, objectPath))
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(30));
            return true;
        } catch (WebClientResponseException.NotFound e) {
            return false;
        } catch (Exception e) {
            log.error("Erreur lors de la suppression Supabase", e);
            return false;
        }
    }

    public String generateSignedUrl(String objectPath, int expiresInSeconds) {
        try {
            WebClient client = getClient();
            JsonNode node = client.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/object/sign/{bucket}/{path}")
                            .build(bucketName, objectPath))
                    .bodyValue(Map.of("expiresIn", expiresInSeconds))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(30));

            if (node != null && node.has("signedURL")) {
                return supabaseUrl + "/storage/v1" + node.get("signedURL").asText();
            }
        } catch (Exception e) {
            log.error("Erreur génération URL signée Supabase", e);
        }
        return null;
    }
}

