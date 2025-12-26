package com.bf4invest.pdf.helper;

import com.lowagie.text.Image;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Helper pour la gestion du logo dans les PDFs
 * Implémente un cache statique pour éviter de recharger le logo à chaque génération
 */
@Slf4j
public class PdfLogoHelper {
    
    private static byte[] cachedLogoBytes = null;
    private static final Object LOGO_LOCK = new Object();
    private static final String LOGO_PATH = "images/logo.png";
    
    /**
     * Charge le logo depuis les ressources et le retourne comme Image iText
     * Utilise un cache statique pour optimiser les performances
     */
    public static Image loadLogo() throws IOException {
        byte[] logoBytes = getLogoBytes();
        return Image.getInstance(logoBytes);
    }
    
    /**
     * Récupère les bytes du logo (avec cache)
     */
    private static byte[] getLogoBytes() throws IOException {
        if (cachedLogoBytes != null) {
            return cachedLogoBytes;
        }
        
        synchronized (LOGO_LOCK) {
            // Double-check locking
            if (cachedLogoBytes != null) {
                return cachedLogoBytes;
            }
            
            try {
                Resource resource = new ClassPathResource(LOGO_PATH);
                if (!resource.exists()) {
                    log.warn("Logo non trouvé à: {}", LOGO_PATH);
                    return null;
                }
                
                try (InputStream is = resource.getInputStream()) {
                    cachedLogoBytes = is.readAllBytes();
                    log.info("Logo chargé et mis en cache ({} bytes)", cachedLogoBytes.length);
                }
            } catch (IOException e) {
                log.error("Erreur lors du chargement du logo: {}", e.getMessage());
                throw e;
            }
            
            return cachedLogoBytes;
        }
    }
    
    /**
     * Réinitialise le cache (utile pour les tests)
     */
    public static void clearCache() {
        synchronized (LOGO_LOCK) {
            cachedLogoBytes = null;
        }
    }
}

