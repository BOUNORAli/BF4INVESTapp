package com.bf4invest.service;

import com.bf4invest.dto.OcrExtractResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Contrat commun pour l'extraction OCR / structuration des documents BC (facture, bon de commande).
 */
public interface DocumentOcrProvider {

    /**
     * Identifiant stable pour logs et diagnostics (ex: "openrouter", "gemini").
     */
    String getProviderId();

    /**
     * Extrait les données structurées depuis une image.
     */
    OcrExtractResult uploadAndExtract(MultipartFile file) throws IOException;

    /**
     * Indique si le provider est utilisable (clé API / config minimale).
     */
    boolean isConfigured();
}
