package com.bf4invest.pdf.helper;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.awt.Color;
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
    
    /**
     * Crée une cellule PDF contenant le logo
     */
    public static PdfPCell createLogoCell(PdfWriter writer, float width, float height) throws DocumentException, IOException {
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBackgroundColor(Color.WHITE);
        logoCell.setFixedHeight(height);
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setPadding(0);
        logoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        
        try {
            Image logoImage = loadLogo();
            if (logoImage != null) {
                float imgW = logoImage.getWidth();
                float imgH = logoImage.getHeight();
                float scale = Math.min((width - 4) / imgW, (height - 4) / imgH);
                logoImage.scaleAbsolute(imgW * scale, imgH * scale);
                logoCell.addElement(logoImage);
            } else {
                logoCell.addElement(new Paragraph("BF4\nINVEST", 
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, PdfColorHelper.BLUE_DARK)));
            }
        } catch (IOException e) {
            log.warn("Erreur chargement logo, utilisation du fallback texte");
            logoCell.addElement(new Paragraph("BF4\nINVEST", 
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, PdfColorHelper.BLUE_DARK)));
        }
        
        return logoCell;
    }
}

