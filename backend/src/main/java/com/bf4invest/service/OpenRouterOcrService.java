package com.bf4invest.service;

import com.bf4invest.dto.OcrExtractResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

/**
 * OCR / extraction structurée via OpenRouter (API compatible OpenAI chat/completions + vision).
 */
@Service
@Slf4j
public class OpenRouterOcrService implements DocumentOcrProvider {

    @Value("${openrouter.api-key:}")
    private String apiKey;

    @Value("${openrouter.api-url:https://openrouter.ai/api/v1}")
    private String apiUrl;

    @Value("${openrouter.model:google/gemini-2.0-flash-exp:free}")
    private String model;

    @Value("${openrouter.http-referer:}")
    private String httpReferer;

    @Value("${openrouter.app-title:BF4 Invest}")
    private String appTitle;

    @Value("${openrouter.request-json-mode:false}")
    private boolean requestJsonMode;

    @Value("${ocr.timeout-seconds.openrouter:60}")
    private int timeoutSeconds;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final OcrJsonParser ocrJsonParser;

    public OpenRouterOcrService(OcrJsonParser ocrJsonParser) {
        this.ocrJsonParser = ocrJsonParser;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getProviderId() {
        return "openrouter";
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.isNotBlank(apiKey);
    }

    public String getModelName() {
        return model;
    }

    @Override
    public OcrExtractResult uploadAndExtract(MultipartFile file) throws IOException {
        log.info("🔍 [OpenRouter OCR] Début extraction - Fichier: {}, Taille: {} bytes",
                file.getOriginalFilename(), file.getSize());

        if (!isConfigured()) {
            throw new IOException("Configuration OpenRouter manquante: openrouter.api-key (OPENROUTER_API_KEY) est requis");
        }

        String base64Image = Base64.getEncoder().encodeToString(file.getBytes());
        String mimeType = file.getContentType();
        if (mimeType == null || !mimeType.startsWith("image/")) {
            mimeType = "image/jpeg";
        }

        String dataUrl = "data:" + mimeType + ";base64," + base64Image;
        String prompt = ocrJsonParser.buildBcInvoicePrompt();

        String jsonResponse = callOpenRouterChatApi(prompt, dataUrl);
        String assistantContent = extractAssistantContent(jsonResponse);
        return ocrJsonParser.parseFromAssistantText(assistantContent);
    }

    private String callOpenRouterChatApi(String prompt, String dataUrl) throws IOException {
        String url = apiUrl.replaceAll("/$", "") + "/chat/completions";

        List<Map<String, Object>> userContent = new ArrayList<>();
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", prompt);
        userContent.add(textPart);

        Map<String, Object> imagePart = new HashMap<>();
        Map<String, Object> imageUrl = new HashMap<>();
        imageUrl.put("url", dataUrl);
        imagePart.put("type", "image_url");
        imagePart.put("image_url", imageUrl);
        userContent.add(imagePart);

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userContent);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(userMessage);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.1);

        if (requestJsonMode) {
            Map<String, Object> responseFormat = new HashMap<>();
            responseFormat.put("type", "json_object");
            requestBody.put("response_format", responseFormat);
        }

        log.debug("📡 [OpenRouter OCR] POST {} model={}", url, model);

        try {
            WebClient.RequestBodySpec spec = webClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json");

            if (StringUtils.isNotBlank(httpReferer)) {
                spec = spec.header("HTTP-Referer", httpReferer);
            }
            if (StringUtils.isNotBlank(appTitle)) {
                spec = spec.header("X-Title", appTitle);
            }

            String response = spec.bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        String msg = parseOpenRouterError(errorBody, clientResponse.statusCode().value());
                                        log.error("❌ [OpenRouter OCR] HTTP {} - {}", clientResponse.statusCode(), msg);
                                        return Mono.error(new IOException(msg));
                                    }))
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(Math.max(10, timeoutSeconds)))
                    .onErrorMap(java.util.concurrent.TimeoutException.class, e ->
                            new IOException("Timeout OpenRouter (" + timeoutSeconds + "s)", e))
                    .block();

            if (response == null || response.trim().isEmpty()) {
                throw new IOException("Réponse vide OpenRouter");
            }
            return response;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ [OpenRouter OCR] Erreur appel API", e);
            throw new IOException("Erreur OpenRouter: " + e.getMessage(), e);
        }
    }

    private String parseOpenRouterError(String errorBody, int status) {
        if (errorBody == null) {
            return "Erreur OpenRouter HTTP " + status;
        }
        try {
            JsonNode root = objectMapper.readTree(errorBody);
            if (root.has("error")) {
                JsonNode err = root.get("error");
                if (err.isTextual()) {
                    return err.asText();
                }
                if (err.has("message")) {
                    return err.get("message").asText();
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return errorBody.length() > 500 ? errorBody.substring(0, 500) + "..." : errorBody;
    }

    private String extractAssistantContent(String jsonResponse) throws IOException {
        JsonNode root = objectMapper.readTree(jsonResponse);

        if (root.has("error")) {
            String msg = parseOpenRouterError(jsonResponse, 0);
            throw new IOException("Erreur OpenRouter: " + msg);
        }

        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new IOException("Réponse OpenRouter invalide: pas de choices");
        }

        JsonNode message = choices.get(0).get("message");
        if (message == null) {
            throw new IOException("Réponse OpenRouter invalide: message absent");
        }

        JsonNode contentNode = message.get("content");
        if (contentNode == null || contentNode.isNull()) {
            throw new IOException("Réponse OpenRouter invalide: content absent");
        }

        if (contentNode.isTextual()) {
            return contentNode.asText();
        }

        if (contentNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : contentNode) {
                if (part.has("text")) {
                    sb.append(part.get("text").asText());
                }
            }
            String combined = sb.toString().trim();
            if (!combined.isEmpty()) {
                return combined;
            }
        }

        throw new IOException("Réponse OpenRouter: format content non supporté");
    }
}
