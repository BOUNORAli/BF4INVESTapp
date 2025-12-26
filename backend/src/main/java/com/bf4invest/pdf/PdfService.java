package com.bf4invest.pdf;

import com.bf4invest.model.*;
import com.bf4invest.pdf.generator.BandeCommandePdfGenerator;
import com.bf4invest.repository.ClientRepository;
import com.bf4invest.repository.SupplierRepository;
import com.bf4invest.repository.BandeCommandeRepository;
import com.bf4invest.service.CompanyInfoService;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfService {
    
    private final BandeCommandePdfGenerator bcGenerator;
    private final CompanyInfoService companyInfoService;
    
    // Dépendances pour les générateurs qui ne sont pas encore refactorisés
    private final ClientRepository clientRepository;
    private final SupplierRepository supplierRepository;
    private final BandeCommandeRepository bandeCommandeRepository;
    
    // Cache statique pour le logo (chargé une seule fois) - conservé pour compatibilité
    private static byte[] cachedLogoBytes = null;
    private static final Object LOGO_LOCK = new Object();
    
    // Couleurs utilisées - conservées pour compatibilité avec code non refactorisé
    private static final Color BLUE_DARK = new Color(30, 64, 124);
    private static final Color BLUE_LIGHT = new Color(200, 220, 240);
    private static final Color BLUE_HEADER = new Color(70, 130, 180);
    private static final Color RED = new Color(180, 0, 0);
    
    // Formatters - conservés pour compatibilité
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat FRENCH_NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.FRENCH);
    static {
        FRENCH_NUMBER_FORMAT.setMinimumFractionDigits(2);
        FRENCH_NUMBER_FORMAT.setMaximumFractionDigits(2);
        FRENCH_NUMBER_FORMAT.setGroupingUsed(true);
    }
    private static final NumberFormat FRENCH_QUANTITY_FORMAT = NumberFormat.getNumberInstance(Locale.FRENCH);
    static {
        FRENCH_QUANTITY_FORMAT.setMinimumFractionDigits(2);
        FRENCH_QUANTITY_FORMAT.setMaximumFractionDigits(2);
        FRENCH_QUANTITY_FORMAT.setGroupingUsed(true);
    }
    
    // Format pour les quantités (avec décimales possibles, format français)
    private static String formatQuantity(Double qty) {
        if (qty == null) return "0,00";
        return FRENCH_QUANTITY_FORMAT.format(qty);
    }
    
    // Format pour les montants (avec 2 décimales et séparateur de milliers)
    private static String formatAmount(Double amount) {
        if (amount == null) return "0,00";
        return FRENCH_NUMBER_FORMAT.format(amount);
    }
    
    /**
     * Génère le PDF d'une bande de commande
     * Délègue au générateur spécialisé
     */
    public byte[] generateBC(BandeCommande bc) throws DocumentException, IOException {
        return bcGenerator.generate(bc);
    }
    
    public byte[] generateFactureVente(FactureVente facture) throws DocumentException, IOException {
        // Marge supérieure augmentée pour laisser de l'espace au logo, inférieure pour le footer
        Document document = new Document(PageSize.A4, 40f, 40f, 80f, 70f);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        
        // IMPORTANT: setPageEvent doit être appelé AVANT document.open()
        writer.setPageEvent(new FactureVenteFooterPageEvent());
        
        document.open();
        
        // Récupérer les informations client
        Client client = facture.getClientId() != null ? 
            clientRepository.findById(facture.getClientId()).orElse(null) : null;
        
        if (client != null) {
            log.debug("Client récupéré pour facture: {} - ICE: {}", client.getNom(), client.getIce());
        } else {
            log.warn("Aucun client trouvé pour la facture ID: {}", facture.getId());
        }
        
        // En-tête avec logo
        addFactureHeader(document, facture, client, writer);
        
        // Informations facture (numéro, date, ref)
        addFactureInfo(document, facture);
        
        // Tableau des lignes
        addFactureProductTable(document, facture);
        
        // Totaux
        addFactureTotals(document, facture);
        
        // Montant en lettres
        addFactureAmountInWords(document, facture);
        
        // Footer géré par FactureVenteFooterPageEvent
        
        document.close();
        return baos.toByteArray();
    }
    
    public byte[] generateFactureAchat(FactureAchat facture) throws DocumentException, IOException {
        // Marge supérieure augmentée pour laisser de l'espace au logo, inférieure pour le footer
        Document document = new Document(PageSize.A4, 40f, 40f, 80f, 70f);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        
        // IMPORTANT: setPageEvent doit être appelé AVANT document.open()
        writer.setPageEvent(new FactureAchatFooterPageEvent());
        
        document.open();
        
        Supplier supplier = facture.getFournisseurId() != null ? 
            supplierRepository.findById(facture.getFournisseurId()).orElse(null) : null;
        
        addFactureAchatHeader(document, facture, supplier, writer);
        addFactureAchatInfo(document, facture);
        addFactureAchatProductTable(document, facture);
        addFactureAchatTotals(document, facture);
        
        // Footer géré par FactureAchatFooterPageEvent
        
        document.close();
        return baos.toByteArray();
    }
    
    public byte[] generateBonDeLivraison(FactureVente facture) throws DocumentException, IOException {
        // Marge supérieure augmentée pour laisser de l'espace au logo, inférieure pour le footer
        Document document = new Document(PageSize.A4, 40f, 40f, 80f, 70f);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        
        // IMPORTANT: setPageEvent doit être appelé AVANT document.open()
        writer.setPageEvent(new BonDeLivraisonFooterPageEvent());
        
        document.open();
        
        // Récupérer les informations client
        Client client = facture.getClientId() != null ? 
            clientRepository.findById(facture.getClientId()).orElse(null) : null;
        
        // En-tête avec logo (identique à la facture)
        addBonDeLivraisonHeader(document, facture, client, writer);
        
        // Informations bon de livraison (numéro, date, ref)
        addBonDeLivraisonInfo(document, facture);
        
        // Tableau des lignes (sans prix)
        addBonDeLivraisonProductTable(document, facture);
        
        // Footer géré par BonDeLivraisonFooterPageEvent
        
        document.close();
        return baos.toByteArray();
    }
    
    // ============ LOGO METHODS ============
    
    /**
     * Récupère les bytes du logo depuis le cache ou les charge depuis les ressources
     * Le logo est chargé une seule fois et mis en cache pour optimiser les performances
     */
    private byte[] getLogoBytes() throws IOException {
        if (cachedLogoBytes != null) {
            return cachedLogoBytes;
        }
        
        synchronized (LOGO_LOCK) {
            if (cachedLogoBytes != null) {
                return cachedLogoBytes;
            }
            
            // Essayer de charger l'image depuis les ressources
            String[] extensions = {"png", "jpg", "jpeg", "gif"};
            for (String ext : extensions) {
                try {
                    ClassPathResource resource = new ClassPathResource("images/logo." + ext);
                    if (resource.exists() && resource.isReadable()) {
                        try (InputStream is = resource.getInputStream()) {
                            cachedLogoBytes = is.readAllBytes();
                            if (cachedLogoBytes != null && cachedLogoBytes.length > 0) {
                                return cachedLogoBytes;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignorer silencieusement, on essaie l'extension suivante
                }
            }
            return null;
        }
    }
    
    /**
     * Crée une cellule avec le logo BF4 INVEST depuis un fichier image
     * Si l'image n'existe pas, crée un logo dessiné programmatiquement
     */
    private PdfPCell createLogoCell(PdfWriter writer, float width, float height) throws DocumentException, IOException {
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBackgroundColor(Color.WHITE); // Fond blanc au lieu de noir
        logoCell.setFixedHeight(height);
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setPadding(0); // Pas de padding pour que l'image remplisse toute la cellule
        logoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        logoCell.setUseAscender(true);
        logoCell.setUseDescender(true);
        
        Image logoImage = null;
        
        // Utiliser le cache pour obtenir les bytes du logo
        byte[] logoBytes = getLogoBytes();
        if (logoBytes != null && logoBytes.length > 0) {
            try {
                logoImage = Image.getInstance(logoBytes);
                float imgW = logoImage.getWidth();
                float imgH = logoImage.getHeight();
                float scale = Math.min((width - 4) / imgW, (height - 4) / imgH);
                logoImage.scaleAbsolute(imgW * scale, imgH * scale);
            } catch (Exception e) {
                // Fallback au logo dessiné
            }
        }
        
        // Si aucune image trouvée, créer un logo dessiné programmatiquement
        if (logoImage == null) {
            try {
                logoImage = createDrawnLogo(writer, width, height);
            } catch (Exception e) {
                // Fallback au texte simple
            }
        }
        
        if (logoImage != null) {
            logoCell.addElement(logoImage);
        } else {
            // Dernier recours: texte simple
            Paragraph fallback = new Paragraph("BF4\nINVEST", 
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(33, 150, 243)));
            fallback.setAlignment(Element.ALIGN_CENTER);
            logoCell.addElement(fallback);
        }
        
        return logoCell;
    }
    
    /**
     * Crée un logo dessiné programmatiquement (fallback si l'image n'existe pas)
     */
    private Image createDrawnLogo(PdfWriter writer, float width, float height) throws DocumentException, IOException {
        PdfTemplate template = writer.getDirectContent().createTemplate(width, height);
        
        // Couleurs pour les lignes courbes
        Color lightBlue = new Color(100, 181, 246); // Bleu clair pour la ligne du haut
        Color darkBlue = new Color(13, 71, 161);    // Bleu foncé/teal pour la ligne du bas
        Color logoBlue = new Color(33, 150, 243);   // Bleu moyen pour le texte
        
        float w = width;
        float h = height;
        float centerX = w / 2;
        float centerY = h / 2;
        
        // Dessiner la ligne courbe du haut (bleu clair) - courbe de Bézier
        template.setColorStroke(lightBlue);
        template.setLineWidth(2.5f);
        template.moveTo(5, h * 0.7f);
        template.curveTo(w * 0.2f, h * 0.85f, w * 0.45f, h * 0.9f, centerX, h * 0.75f);
        template.curveTo(w * 0.55f, h * 0.9f, w * 0.8f, h * 0.85f, w - 5, h * 0.7f);
        template.stroke();
        
        // Dessiner la ligne courbe du bas (bleu foncé) - courbe de Bézier
        template.setColorStroke(darkBlue);
        template.setLineWidth(2.5f);
        template.moveTo(5, h * 0.3f);
        template.curveTo(w * 0.2f, h * 0.15f, w * 0.45f, h * 0.1f, centerX, h * 0.25f);
        template.curveTo(w * 0.55f, h * 0.1f, w * 0.8f, h * 0.15f, w - 5, h * 0.3f);
        template.stroke();
        
        // Ajouter le texte "BF4 INVEST" au centre
        BaseFont baseFont = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        template.beginText();
        template.setFontAndSize(baseFont, 18);
        template.setColorFill(logoBlue);
        
        // Centrer le texte
        String text = "BF4 INVEST";
        float textWidth = baseFont.getWidthPoint(text, 18);
        float textX = (w - textWidth) / 2;
        float textY = centerY - 6;
        
        template.setTextMatrix(textX, textY);
        template.showText(text);
        template.endText();
        
        return Image.getInstance(template);
    }
    
    // ============ BON DE COMMANDE METHODS ============
    
    private void addBCHeader(Document document, BandeCommande bc, PdfWriter writer) throws DocumentException, IOException {
        // Table pour le header (logo à gauche, contenu à droite)
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{2f, 8f});
        headerTable.setSpacingAfter(10);
        
        // Logo stylisé avec lignes courbes - plus grand et en haut
        PdfPCell logoCell = createLogoCell(writer, 100f, 75f);
        logoCell.setVerticalAlignment(Element.ALIGN_TOP);
        headerTable.addCell(logoCell);
        
        // Contenu à droite : titre + numéro
        PdfPCell contentCell = new PdfPCell();
        contentCell.setBorder(Rectangle.NO_BORDER);
        contentCell.setPadding(0);
        contentCell.setVerticalAlignment(Element.ALIGN_TOP);
        
        // "BON DE COMMANDE N° [numéro]" centré
        Paragraph title = new Paragraph();
        Chunk titleChunk = new Chunk("BON DE COMMANDE N°", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));
        title.add(titleChunk);
        String numeroBC = bc.getNumeroBC() != null ? bc.getNumeroBC() : "";
        Chunk numeroChunk = new Chunk(" " + numeroBC, 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, RED));
        title.add(numeroChunk);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(0);
        contentCell.addElement(title);
        
        headerTable.addCell(contentCell);
        
        document.add(headerTable);
    }
    
    private void addBCDestinataire(Document document, BandeCommande bc, Supplier supplier) throws DocumentException {
        PdfPTable destTable = new PdfPTable(3);
        destTable.setWidthPercentage(100);
        destTable.setSpacingAfter(15);
        destTable.setWidths(new float[]{2f, 4f, 4f});
        
        // Cellule gauche avec fond bleu clair - "DESTINATAIRE :"
        PdfPCell labelCell = new PdfPCell(new Phrase("DESTINATAIRE :", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        labelCell.setBackgroundColor(BLUE_LIGHT);
        labelCell.setPadding(8);
        labelCell.setBorder(Rectangle.BOX);
        labelCell.setBorderColor(Color.WHITE);
        labelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        destTable.addCell(labelCell);
        
        // Cellule milieu avec nom du fournisseur en rouge et souligné
        PdfPCell nameCell = new PdfPCell();
        nameCell.setBorder(Rectangle.BOX);
        nameCell.setBorderColor(Color.WHITE);
        nameCell.setPadding(8);
        nameCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        nameCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        String supplierName = supplier != null ? supplier.getNom() : "";
        Font nameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.UNDERLINE, RED);
        Phrase name = new Phrase(supplierName, nameFont);
        nameCell.addElement(name);
        destTable.addCell(nameCell);
        
        // Cellule droite avec "MEKNES LE: [date]" aligné à droite
        PdfPCell dateCell = new PdfPCell();
        dateCell.setBorder(Rectangle.NO_BORDER);
        dateCell.setPadding(8);
        dateCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        dateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        LocalDate dateBC = bc.getDateBC() != null ? bc.getDateBC() : LocalDate.now();
        String dateStr = dateBC.format(DATE_FORMATTER);
        Paragraph dateLine = new Paragraph();
        dateLine.add(new Chunk("MEKNES LE: ", FontFactory.getFont(FontFactory.HELVETICA, 10)));
        dateLine.add(new Chunk(dateStr, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, RED)));
        dateLine.setAlignment(Element.ALIGN_RIGHT);
        dateCell.addElement(dateLine);
        destTable.addCell(dateCell);
        
        document.add(destTable);
    }
    
    private void addBCProductTable(Document document, BandeCommande bc) throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{0.5f, 3f, 1f, 1.5f, 2f, 2.5f});
        table.setSpacingAfter(15);
        
        // En-têtes avec fond bleu clair et texte bleu foncé
        String[] headers = {"N°", "Désignation", "Unité", "Quantité", "PU HT", "Prix Total HT"};
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BLUE_HEADER);
        
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(BLUE_LIGHT);
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBorderColor(Color.WHITE);
            table.addCell(cell);
        }
        
        double totalHT = 0.0;
        double tauxTVA = 20.0;
        
        // Nouvelle structure: lignesAchat
        if (bc.getLignesAchat() != null && !bc.getLignesAchat().isEmpty()) {
            int lineNum = 1;
            for (var ligne : bc.getLignesAchat()) {
                // N°
                addTableCell(table, String.valueOf(lineNum++), Element.ALIGN_CENTER);
                
                // Désignation
                addTableCell(table, ligne.getDesignation() != null ? ligne.getDesignation() : "", 
                    Element.ALIGN_LEFT);
                
                // Unité
                addTableCell(table, ligne.getUnite() != null ? ligne.getUnite() : "", 
                    Element.ALIGN_CENTER);
                
                // Quantité (format français avec décimales possibles)
                Double qtyValue = ligne.getQuantiteAchetee() != null ? ligne.getQuantiteAchetee() : 0.0;
                String qty = formatQuantity(qtyValue);
                addTableCell(table, qty, Element.ALIGN_RIGHT);
                
                // PU HT (format avec 2 décimales)
                String puHT = ligne.getPrixAchatUnitaireHT() != null ? 
                    formatAmount(ligne.getPrixAchatUnitaireHT()) : "0,00";
                addTableCell(table, puHT, Element.ALIGN_RIGHT);
                
                // Prix Total HT
                double lineTotalHT = (ligne.getQuantiteAchetee() != null ? ligne.getQuantiteAchetee() : 0) * 
                    (ligne.getPrixAchatUnitaireHT() != null ? ligne.getPrixAchatUnitaireHT() : 0);
                addTableCell(table, formatAmount(lineTotalHT), Element.ALIGN_RIGHT);

                // Total global + TVA
                totalHT += lineTotalHT;
                if (ligne.getTva() != null && ligne.getTva() > 0) {
                    tauxTVA = ligne.getTva();
                }
            }
        }
        // Rétrocompatibilité: ancienne structure lignes
        else if (bc.getLignes() != null && !bc.getLignes().isEmpty()) {
            int lineNum = 1;
            for (var ligne : bc.getLignes()) {
                // N°
                addTableCell(table, String.valueOf(lineNum++), Element.ALIGN_CENTER);
                
                // Désignation
                addTableCell(table, ligne.getDesignation() != null ? ligne.getDesignation() : "", 
                    Element.ALIGN_LEFT);
                
                // Unité
                addTableCell(table, ligne.getUnite() != null ? ligne.getUnite() : "", 
                    Element.ALIGN_CENTER);
                
                // Quantité (format français avec décimales possibles)
                Double qtyValue = ligne.getQuantiteAchetee() != null ? ligne.getQuantiteAchetee().doubleValue() : 0.0;
                String qty = formatQuantity(qtyValue);
                addTableCell(table, qty, Element.ALIGN_RIGHT);
                
                // PU HT (format avec 2 décimales)
                String puHT = ligne.getPrixAchatUnitaireHT() != null ? 
                    formatAmount(ligne.getPrixAchatUnitaireHT()) : "0,00";
                addTableCell(table, puHT, Element.ALIGN_RIGHT);
                
                // Prix Total HT
                double lineTotalHT = (ligne.getQuantiteAchetee() != null ? ligne.getQuantiteAchetee() : 0) * 
                    (ligne.getPrixAchatUnitaireHT() != null ? ligne.getPrixAchatUnitaireHT() : 0);
                addTableCell(table, formatAmount(lineTotalHT), Element.ALIGN_RIGHT);

                totalHT += lineTotalHT;
            }
        }

        // Fallback totaux si on n'a aucune ligne mais un total pré-calculé
        if (totalHT == 0.0 && bc.getTotalAchatHT() != null) {
            totalHT = bc.getTotalAchatHT();
        }

        addBCTotalsRowsInProductTable(table, totalHT, tauxTVA);
        
        document.add(table);
    }

    /**
     * Ajoute les lignes TOTAL HT / TVA A / TOTAL TTC dans le même tableau que les produits,
     * pour correspondre au modèle.
     */
    private void addBCTotalsRowsInProductTable(PdfPTable table, double totalHT, double tauxTVA) {
        double montantTVA = totalHT * (tauxTVA / 100.0);
        double totalTTC = totalHT + montantTVA;

        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font valueFontStrong = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font tvaRateFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, RED);

        // TOTAL HT (label sur 5 colonnes + montant sur la dernière colonne)
        PdfPCell totalHtLabel = new PdfPCell(new Phrase("TOTAL HT", labelFont));
        totalHtLabel.setColspan(5);
        totalHtLabel.setPadding(6);
        totalHtLabel.setBorder(Rectangle.BOX);
        totalHtLabel.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(totalHtLabel);

        PdfPCell totalHtValue = new PdfPCell(new Phrase(formatAmount(totalHT), valueFont));
        totalHtValue.setPadding(6);
        totalHtValue.setBorder(Rectangle.BOX);
        totalHtValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(totalHtValue);

        // TVA A (label sur 4 colonnes + taux sur 1 colonne + montant sur la dernière colonne)
        PdfPCell tvaLabel = new PdfPCell(new Phrase("TVA A", labelFont));
        tvaLabel.setColspan(4);
        tvaLabel.setPadding(6);
        tvaLabel.setBorder(Rectangle.BOX);
        tvaLabel.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(tvaLabel);

        PdfPCell tvaRateCell = new PdfPCell(new Phrase(String.format("%.0f%%", tauxTVA), tvaRateFont));
        tvaRateCell.setPadding(6);
        tvaRateCell.setBorder(Rectangle.BOX);
        tvaRateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(tvaRateCell);

        PdfPCell tvaValue = new PdfPCell(new Phrase(formatAmount(montantTVA), valueFont));
        tvaValue.setPadding(6);
        tvaValue.setBorder(Rectangle.BOX);
        tvaValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(tvaValue);

        // TOTAL TTC
        PdfPCell totalTtcLabel = new PdfPCell(new Phrase("TOTAL TTC", labelFont));
        totalTtcLabel.setColspan(5);
        totalTtcLabel.setPadding(6);
        totalTtcLabel.setBorder(Rectangle.BOX);
        totalTtcLabel.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(totalTtcLabel);

        PdfPCell totalTtcValue = new PdfPCell(new Phrase(formatAmount(totalTTC), valueFontStrong));
        totalTtcValue.setPadding(6);
        totalTtcValue.setBorder(Rectangle.BOX);
        totalTtcValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(totalTtcValue);
    }
    
    private void addBCTotals(Document document, BandeCommande bc) throws DocumentException {
        PdfPTable totalsTable = new PdfPTable(4);
        totalsTable.setWidthPercentage(60);
        totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalsTable.setWidths(new float[]{2f, 1.5f, 1.5f, 2f});
        totalsTable.setSpacingAfter(20);
        
        // Calculer les totaux
        double totalHT = 0.0;
        double tauxTVA = 20.0;
        
        // Nouvelle structure: lignesAchat
        if (bc.getLignesAchat() != null && !bc.getLignesAchat().isEmpty()) {
            for (var ligne : bc.getLignesAchat()) {
                double qty = ligne.getQuantiteAchetee() != null ? ligne.getQuantiteAchetee() : 0;
                double puHT = ligne.getPrixAchatUnitaireHT() != null ? ligne.getPrixAchatUnitaireHT() : 0;
                totalHT += qty * puHT;
                if (ligne.getTva() != null && ligne.getTva() > 0) {
                    tauxTVA = ligne.getTva();
                }
            }
        }
        // Rétrocompatibilité: ancienne structure lignes
        else if (bc.getLignes() != null && !bc.getLignes().isEmpty()) {
            for (var ligne : bc.getLignes()) {
                double qty = ligne.getQuantiteAchetee() != null ? ligne.getQuantiteAchetee() : 0;
                double puHT = ligne.getPrixAchatUnitaireHT() != null ? ligne.getPrixAchatUnitaireHT() : 0;
                totalHT += qty * puHT;
            }
        }
        // Utiliser le total pré-calculé si disponible
        else if (bc.getTotalAchatHT() != null) {
            totalHT = bc.getTotalAchatHT();
        }
        
        double montantTVA = totalHT * (tauxTVA / 100);
        double totalTTC = totalHT + montantTVA;
        
        // TOTAL HT
        addTotalsRow(totalsTable, "TOTAL HT", formatAmount(totalHT), false);
        
        // TVA A
        PdfPCell tvaLabel = new PdfPCell(new Phrase("TVA A", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        tvaLabel.setBorder(Rectangle.NO_BORDER);
        tvaLabel.setPadding(5);
        totalsTable.addCell(tvaLabel);
        
        PdfPCell tvaRate = new PdfPCell(new Phrase(String.format("%.0f%%", tauxTVA), 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, RED)));
        tvaRate.setBorder(Rectangle.NO_BORDER);
        tvaRate.setPadding(5);
        totalsTable.addCell(tvaRate);
        
        PdfPCell tvaAmount = new PdfPCell(new Phrase(formatAmount(montantTVA), 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        tvaAmount.setBorder(Rectangle.NO_BORDER);
        tvaAmount.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tvaAmount.setPadding(5);
        totalsTable.addCell(tvaAmount);
        
        // TOTAL TTC
        addTotalsRow(totalsTable, "TOTAL TTC", formatAmount(totalTTC), true);
        
        document.add(totalsTable);
    }
    
    private void addBCDeliveryInfo(Document document, BandeCommande bc, Client client) throws DocumentException {
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setSpacingAfter(30);
        infoTable.setWidths(new float[]{3f, 4f});
        
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font valueFontRed = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, RED);
        
        // LIEU DE LIVRAISON (vient du BC)
        addInfoRowWithBackground(infoTable, "LIEU DE LIVRAISON:", 
            bc.getLieuLivraison() != null ? bc.getLieuLivraison() : "",
            labelFont, valueFontRed);
        
        // CONDITION DE LIVRAISON (vient du BC) - laisser vide si non renseigné
        addInfoRowWithBackground(infoTable, "CONDITION DE LIVRAISON:",
            bc.getConditionLivraison() != null ? bc.getConditionLivraison() : "",
            labelFont, valueFontRed);
        
        // RESPONSABLE A CONTACTER (vient du BC) - laisser vide si non renseigné
        String responsable = bc.getResponsableLivraison() != null ? bc.getResponsableLivraison() : "";
        addInfoRowWithBackground(infoTable, "RESPONSABLE A CONTACTER A LA\nLIVRAISON", responsable, 
            labelFont, valueFontRed);
        
        // MODE PAIEMENT (délai de paiement en jours, ex: "120J") - laisser vide si non renseigné
        String delaiPaiement = bc.getDelaiPaiement() != null && !bc.getDelaiPaiement().isEmpty() 
            ? bc.getDelaiPaiement() 
            : "";
        addInfoRowWithBackground(infoTable, "MODE PAIEMENT :", delaiPaiement, labelFont, valueFontRed);
        
        document.add(infoTable);
    }
    
    private void addBCFooter(Document document) throws DocumentException {
        PdfPTable footerTable = new PdfPTable(1);
        footerTable.setWidthPercentage(100);
        footerTable.setSpacingBefore(30);
        footerTable.setHorizontalAlignment(Element.ALIGN_CENTER);
        
        PdfPCell footerCell = new PdfPCell();
        footerCell.setBackgroundColor(BLUE_LIGHT);
        footerCell.setPadding(10);
        footerCell.setBorder(Rectangle.NO_BORDER);
        footerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        
        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
        
        Paragraph footer1 = new Paragraph("ICE: 002889872000062", footerFont);
        footer1.setAlignment(Element.ALIGN_CENTER);
        footer1.setSpacingAfter(3);
        
        Paragraph footer2 = new Paragraph("BF4 INVEST SARL au capital de 2.000.000,00 Dhs, Tel: 06 61 51 11 91", footerFont);
        footer2.setAlignment(Element.ALIGN_CENTER);
        footer2.setSpacingAfter(3);
        
        Paragraph footer3 = new Paragraph("RC de Meknes: 54287 - IF: 50499801 - TP: 17101980", footerFont);
        footer3.setAlignment(Element.ALIGN_CENTER);
        footer3.setSpacingAfter(0);
        
        footerCell.addElement(footer1);
        footerCell.addElement(footer2);
        footerCell.addElement(footer3);
        
        footerTable.addCell(footerCell);
        document.add(footerTable);
    }
    
    /**
     * Classe interne pour gérer le footer et le logo sur toutes les pages du BC
     */
    private class BCFooterPageEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                PdfContentByte canvas = writer.getDirectContent();
                
                // Ajouter le logo en haut à gauche sur toutes les pages (sauf la première où il est déjà dans le header)
                if (writer.getPageNumber() > 1) {
                    addLogoToPage(canvas, writer, document);
                }
                
                // Ajouter le footer en bas de page
                PdfPTable footerTable = new PdfPTable(1);
                float tableWidth = document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin();
                footerTable.setTotalWidth(tableWidth);
                footerTable.setLockedWidth(true);
                
                PdfPCell footerCell = new PdfPCell();
                footerCell.setBackgroundColor(BLUE_LIGHT);
                footerCell.setPadding(10);
                footerCell.setBorder(Rectangle.NO_BORDER);
                footerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                
                Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
                addCompanyFooterParagraphs(footerCell, footerFont);
                
                footerTable.addCell(footerCell);
                
                // Positionner le footer en bas de page
                // Le système de coordonnées iText commence en bas à gauche
                float yPosition = document.bottomMargin() + 2f; // 2 points de marge minimale
                footerTable.writeSelectedRows(0, -1, document.leftMargin(), yPosition, canvas);
            } catch (DocumentException | IOException e) {
                log.error("Error adding BC footer/logo to page", e);
            }
        }
        
        /**
         * Ajoute le logo en haut à gauche de la page
         */
        private void addLogoToPage(PdfContentByte canvas, PdfWriter writer, Document document) throws DocumentException, IOException {
            try {
                float logoWidth = 100f;
                float logoHeight = 75f;
                float xPosition = document.leftMargin();
                // Positionner le logo tout en haut de la page, au-dessus du tableau
                float yPosition = document.getPageSize().getHeight() - 2f; // 2 points sous le bord haut
                
                // Créer une table temporaire pour le logo
                PdfPTable logoTable = new PdfPTable(1);
                logoTable.setTotalWidth(logoWidth);
                logoTable.setLockedWidth(true);
                
                PdfPCell logoCell = createLogoCell(writer, logoWidth, logoHeight);
                logoTable.addCell(logoCell);
                
                // Positionner le logo en haut à gauche
                logoTable.writeSelectedRows(0, -1, xPosition, yPosition, canvas);
            } catch (Exception e) {
                log.error("Error adding logo to page", e);
            }
        }
    }
    
    /**
     * Classe interne pour gérer le footer et le logo sur toutes les pages de la Facture Vente
     */
    private class FactureVenteFooterPageEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                PdfContentByte canvas = writer.getDirectContent();
                
                // Ajouter le logo en haut à gauche sur toutes les pages (sauf la première où il est déjà dans le header)
                if (writer.getPageNumber() > 1) {
                    addLogoToPage(canvas, writer, document);
                }
                
                // Ajouter le footer en bas de page
                PdfPTable footerTable = new PdfPTable(1);
                float tableWidth = document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin();
                footerTable.setTotalWidth(tableWidth);
                footerTable.setLockedWidth(true);
                
                PdfPCell footerCell = new PdfPCell();
                footerCell.setBackgroundColor(BLUE_LIGHT);
                footerCell.setPadding(10);
                footerCell.setBorder(Rectangle.NO_BORDER);
                footerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                
                Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
                addCompanyFooterParagraphs(footerCell, footerFont);
                
                footerTable.addCell(footerCell);
                
                // Positionner le footer en bas de page
                float yPosition = document.bottomMargin() + 2f;
                footerTable.writeSelectedRows(0, -1, document.leftMargin(), yPosition, canvas);
            } catch (DocumentException | IOException e) {
                log.error("Error adding Facture Vente footer/logo to page", e);
            }
        }
        
        private void addLogoToPage(PdfContentByte canvas, PdfWriter writer, Document document) throws DocumentException, IOException {
            try {
                float logoWidth = 100f;
                float logoHeight = 75f;
                float xPosition = document.leftMargin();
                float yPosition = document.getPageSize().getHeight() - 2f;
                
                PdfPTable logoTable = new PdfPTable(1);
                logoTable.setTotalWidth(logoWidth);
                logoTable.setLockedWidth(true);
                
                PdfPCell logoCell = createLogoCell(writer, logoWidth, logoHeight);
                logoTable.addCell(logoCell);
                
                logoTable.writeSelectedRows(0, -1, xPosition, yPosition, canvas);
            } catch (Exception e) {
                log.error("Error adding logo to Facture Vente page", e);
            }
        }
    }
    
    // ============ FACTURE VENTE METHODS ============
    
    private void addFactureHeader(Document document, FactureVente facture, Client client, PdfWriter writer) throws DocumentException, IOException {
        PdfPTable headerTable = new PdfPTable(3);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{2f, 4f, 4f});
        headerTable.setSpacingAfter(15);
        
        // Logo stylisé avec lignes courbes - agrandi à 100x75
        PdfPCell logoCell = createLogoCell(writer, 100f, 75f);
        headerTable.addCell(logoCell);
        
        // Espace vide
        PdfPCell emptyCell = new PdfPCell();
        emptyCell.setBorder(Rectangle.NO_BORDER);
        headerTable.addCell(emptyCell);
        
        // Client info box avec bordure noire
        PdfPCell clientCell = new PdfPCell();
        clientCell.setBorder(Rectangle.BOX);
        clientCell.setBorderColor(Color.BLACK);
        clientCell.setPadding(8);
        clientCell.setBackgroundColor(Color.WHITE);
        
        Paragraph clientLabel = new Paragraph("CLIENT:", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9));
        clientCell.addElement(clientLabel);
        
        String clientName = client != null ? client.getNom() : "";
        Paragraph clientNameText = new Paragraph(clientName, 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10));
        // Souligner le nom du client comme dans l'exemple
        clientNameText.getFont().setStyle(Font.UNDERLINE);
        clientCell.addElement(clientNameText);
        
        Paragraph iceLabel = new Paragraph("ICE:", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9));
        iceLabel.setSpacingBefore(5);
        clientCell.addElement(iceLabel);
        
        // Récupérer l'ICE depuis le client dans la base de données
        String ice = "";
        if (client != null) {
            ice = client.getIce() != null ? client.getIce() : "";
            if (ice.isEmpty()) {
                log.warn("Client {} n'a pas d'ICE défini", client.getNom());
            }
        } else {
            log.warn("Client est null, impossible de récupérer l'ICE");
        }
        
        Paragraph iceText = new Paragraph(ice, 
            FontFactory.getFont(FontFactory.HELVETICA, 9));
        // Souligner l'ICE comme dans l'exemple
        iceText.getFont().setStyle(Font.UNDERLINE);
        clientCell.addElement(iceText);
        
        headerTable.addCell(clientCell);
        document.add(headerTable);
    }
    
    private void addFactureInfo(Document document, FactureVente facture) throws DocumentException {
        PdfPTable infoTable = new PdfPTable(3);
        infoTable.setWidthPercentage(100);
        infoTable.setSpacingAfter(15);
        
        // FACTURE N° avec fond bleu clair
        PdfPCell factNumCell = new PdfPCell();
        factNumCell.setBackgroundColor(BLUE_LIGHT);
        factNumCell.setPadding(8);
        factNumCell.setBorder(Rectangle.NO_BORDER);
        
        Paragraph factLabel = new Paragraph("FACTURE N°", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10));
        factNumCell.addElement(factLabel);
        
        String factNum = facture.getNumeroFactureVente() != null ? facture.getNumeroFactureVente() : "";
        // Mettre en avant le numéro de facture avec une couleur contrastée
        Paragraph factNumText = new Paragraph(factNum, 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BLUE_DARK));
        factNumCell.addElement(factNumText);
        infoTable.addCell(factNumCell);
        
        // DU: (date)
        PdfPCell dateCell = new PdfPCell();
        dateCell.setBorder(Rectangle.NO_BORDER);
        dateCell.setPadding(8);
        Paragraph dateLabel = new Paragraph("DU:", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9));
        dateCell.addElement(dateLabel);
        
        String dateStr = facture.getDateFacture() != null ? 
            facture.getDateFacture().format(DATE_FORMATTER) : "";
        Paragraph dateText = new Paragraph(dateStr, 
            FontFactory.getFont(FontFactory.HELVETICA, 9));
        dateCell.addElement(dateText);
        infoTable.addCell(dateCell);
        
        // Ref: N°
        PdfPCell refCell = new PdfPCell();
        refCell.setBorder(Rectangle.NO_BORDER);
        refCell.setPadding(8);
        Paragraph refLabel = new Paragraph("Ref: N°", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9));
        refCell.addElement(refLabel);
        
        // Récupérer le numéro BC si disponible
        String refBC = "";
        if (facture.getBandeCommandeId() != null) {
            BandeCommande bc = bandeCommandeRepository.findById(facture.getBandeCommandeId()).orElse(null);
            if (bc != null && bc.getNumeroBC() != null) {
                refBC = bc.getNumeroBC();
            }
        }
        Paragraph refText = new Paragraph(refBC, 
            FontFactory.getFont(FontFactory.HELVETICA, 9));
        refCell.addElement(refText);
        infoTable.addCell(refCell);
        
        document.add(infoTable);
    }
    
    private void addFactureProductTable(Document document, FactureVente facture) throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{0.5f, 3f, 1f, 1.5f, 1.5f, 2f});
        table.setSpacingAfter(15);
        
        // En-têtes avec fond bleu clair et texte bleu foncé
        String[] headers = {"N° ARTICLE", "DESIGNATIONS ET PRESTATIONS", "UNITE", "QUANTITE", "PU HT", "Prix Total HT"};
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BLUE_HEADER);
        
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(BLUE_LIGHT);
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBorderColor(Color.WHITE);
            table.addCell(cell);
        }
        
        // Lignes
        if (facture.getLignes() != null && !facture.getLignes().isEmpty()) {
            int lineNum = 1;
            for (var ligne : facture.getLignes()) {
                addTableCell(table, String.valueOf(lineNum++), Element.ALIGN_CENTER);
                addTableCell(table, ligne.getDesignation() != null ? ligne.getDesignation() : "", 
                    Element.ALIGN_LEFT);
                addTableCell(table, ligne.getUnite() != null ? ligne.getUnite() : "", 
                    Element.ALIGN_CENTER);
                
                // Quantité (format français avec décimales possibles)
                Double qtyValue = ligne.getQuantiteVendue() != null ? ligne.getQuantiteVendue().doubleValue() : 0.0;
                String qty = formatQuantity(qtyValue);
                addTableCell(table, qty, Element.ALIGN_RIGHT);
                
                String puHT = ligne.getPrixVenteUnitaireHT() != null ? 
                    formatAmount(ligne.getPrixVenteUnitaireHT()) : "0,00";
                addTableCell(table, puHT, Element.ALIGN_RIGHT);
                
                double totalHT = (ligne.getQuantiteVendue() != null ? ligne.getQuantiteVendue() : 0) * 
                    (ligne.getPrixVenteUnitaireHT() != null ? ligne.getPrixVenteUnitaireHT() : 0);
                addTableCell(table, formatAmount(totalHT), Element.ALIGN_RIGHT);
            }
        }
        
        document.add(table);
    }
    
    private void addFactureTotals(Document document, FactureVente facture) throws DocumentException {
        PdfPTable totalsTable = new PdfPTable(4);
        totalsTable.setWidthPercentage(60);
        totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalsTable.setWidths(new float[]{2f, 1.5f, 1.5f, 2f});
        totalsTable.setSpacingAfter(10);
        
        double totalHT = facture.getTotalHT() != null ? facture.getTotalHT() : 0.0;
        double tauxTVA = 20.0;
        double montantTVA = facture.getTotalTVA() != null ? facture.getTotalTVA() : (totalHT * (tauxTVA / 100));
        double totalTTC = facture.getTotalTTC() != null ? facture.getTotalTTC() : (totalHT + montantTVA);
        
        addTotalsRow(totalsTable, "TOTAL HT", formatAmount(totalHT), false);
        
        // TVA A
        PdfPCell tvaLabel = new PdfPCell(new Phrase("TVA A", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        tvaLabel.setBorder(Rectangle.NO_BORDER);
        tvaLabel.setPadding(5);
        totalsTable.addCell(tvaLabel);
        
        PdfPCell tvaRate = new PdfPCell(new Phrase(String.format("%.0f%%", tauxTVA), 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        tvaRate.setBorder(Rectangle.NO_BORDER);
        tvaRate.setPadding(5);
        totalsTable.addCell(tvaRate);
        
        // Cellule vide pour que le montant TVA soit aligné verticalement avec les montants TOTAL HT / TOTAL TTC
        PdfPCell tvaEmpty = new PdfPCell();
        tvaEmpty.setBorder(Rectangle.NO_BORDER);
        totalsTable.addCell(tvaEmpty);
        
        PdfPCell tvaAmount = new PdfPCell(new Phrase(formatAmount(montantTVA), 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        tvaAmount.setBorder(Rectangle.NO_BORDER);
        tvaAmount.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tvaAmount.setPadding(5);
        totalsTable.addCell(tvaAmount);
        
        // TOTAL TTC (sur une nouvelle ligne, même alignement que TOTAL HT)
        addTotalsRow(totalsTable, "TOTAL TTC", formatAmount(totalTTC), true);
        
        document.add(totalsTable);
    }
    
    private void addFactureAmountInWords(Document document, FactureVente facture) throws DocumentException {
        double totalTTC = facture.getTotalTTC() != null ? facture.getTotalTTC() : 0.0;
        String amountInWords = convertAmountToFrenchWords(totalTTC);
        
        // Ligne de titre soulignée
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        titleFont.setStyle(Font.UNDERLINE);
        Paragraph title = new Paragraph("Arrêter la présente facture à la somme TTC de :", titleFont);
        title.setSpacingBefore(15);
        title.setSpacingAfter(5);
        document.add(title);
        
        // Montant en toutes lettres, plus lisible et en bleu foncé
        Font amountFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BLUE_DARK);
        Paragraph amountLine = new Paragraph(amountInWords, amountFont);
        amountLine.setSpacingAfter(25);
        document.add(amountLine);
    }
    
    private void addFactureFooter(Document document) throws DocumentException {
        PdfPTable footerTable = new PdfPTable(1);
        footerTable.setWidthPercentage(100);
        footerTable.setHorizontalAlignment(Element.ALIGN_CENTER);
        
        PdfPCell footerCell = new PdfPCell();
        footerCell.setBackgroundColor(BLUE_LIGHT);
        footerCell.setPadding(10);
        footerCell.setBorder(Rectangle.NO_BORDER);
        footerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        
        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
        addCompanyFooterParagraphs(footerCell, footerFont);
        
        footerTable.addCell(footerCell);
        document.add(footerTable);
    }
    
    /**
     * Classe interne pour gérer le footer et le logo sur toutes les pages du Bon de Livraison
     */
    private class BonDeLivraisonFooterPageEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                PdfContentByte canvas = writer.getDirectContent();
                
                // Ajouter le logo en haut à gauche sur toutes les pages (sauf la première où il est déjà dans le header)
                if (writer.getPageNumber() > 1) {
                    addLogoToPage(canvas, writer, document);
                }
                
                // Ajouter le footer en bas de page
                PdfPTable footerTable = new PdfPTable(1);
                float tableWidth = document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin();
                footerTable.setTotalWidth(tableWidth);
                footerTable.setLockedWidth(true);
                
                PdfPCell footerCell = new PdfPCell();
                footerCell.setBackgroundColor(BLUE_LIGHT);
                footerCell.setPadding(10);
                footerCell.setBorder(Rectangle.NO_BORDER);
                footerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                
                Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
                
                Paragraph footer1 = new Paragraph("ICE: 002889872000062", footerFont);
                footer1.setAlignment(Element.ALIGN_CENTER);
                footer1.setSpacingAfter(3);
                
                Paragraph footer2 = new Paragraph("BF4 INVEST SARL au capital de 2.000.000,00 Dhs, Tel: 06 61 51 11 91", footerFont);
                footer2.setAlignment(Element.ALIGN_CENTER);
                footer2.setSpacingAfter(3);
                
                Paragraph footer3 = new Paragraph("RC de Meknes: 54287 - IF: 50499801 - TP: 17101980", footerFont);
                footer3.setAlignment(Element.ALIGN_CENTER);
                footer3.setSpacingAfter(0);
                
                footerCell.addElement(footer1);
                footerCell.addElement(footer2);
                footerCell.addElement(footer3);
                
                footerTable.addCell(footerCell);
                
                // Positionner le footer en bas de page
                float yPosition = document.bottomMargin() + 2f;
                footerTable.writeSelectedRows(0, -1, document.leftMargin(), yPosition, canvas);
            } catch (DocumentException | IOException e) {
                log.error("Error adding Bon de Livraison footer/logo to page", e);
            }
        }
        
        private void addLogoToPage(PdfContentByte canvas, PdfWriter writer, Document document) throws DocumentException, IOException {
            try {
                float logoWidth = 100f;
                float logoHeight = 75f;
                float xPosition = document.leftMargin();
                float yPosition = document.getPageSize().getHeight() - 2f;
                
                PdfPTable logoTable = new PdfPTable(1);
                logoTable.setTotalWidth(logoWidth);
                logoTable.setLockedWidth(true);
                
                PdfPCell logoCell = createLogoCell(writer, logoWidth, logoHeight);
                logoTable.addCell(logoCell);
                
                logoTable.writeSelectedRows(0, -1, xPosition, yPosition, canvas);
            } catch (Exception e) {
                log.error("Error adding logo to Bon de Livraison page", e);
            }
        }
    }
    
    // ============ BON DE LIVRAISON METHODS ============
    
    private void addBonDeLivraisonHeader(Document document, FactureVente facture, Client client, PdfWriter writer) throws DocumentException, IOException {
        // Identique à addFactureHeader
        addFactureHeader(document, facture, client, writer);
    }
    
    private void addBonDeLivraisonInfo(Document document, FactureVente facture) throws DocumentException {
        PdfPTable infoTable = new PdfPTable(3);
        infoTable.setWidthPercentage(100);
        infoTable.setSpacingAfter(15);
        
        // BON DE LIVRAISON N° avec fond bleu clair
        PdfPCell blNumCell = new PdfPCell();
        blNumCell.setBackgroundColor(BLUE_LIGHT);
        blNumCell.setPadding(8);
        blNumCell.setBorder(Rectangle.NO_BORDER);
        
        Paragraph blLabel = new Paragraph("BON DE LIVRAISON N°", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10));
        blNumCell.addElement(blLabel);
        
        // Utiliser le numéro de facture ou générer un numéro de BL
        String blNum = facture.getNumeroFactureVente() != null ? 
            "BL-" + facture.getNumeroFactureVente() : "";
        Paragraph blNumText = new Paragraph(blNum, 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
        blNumCell.addElement(blNumText);
        infoTable.addCell(blNumCell);
        
        // DU: (date)
        PdfPCell dateCell = new PdfPCell();
        dateCell.setBorder(Rectangle.NO_BORDER);
        dateCell.setPadding(8);
        Paragraph dateLabel = new Paragraph("DU:", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9));
        dateCell.addElement(dateLabel);
        
        String dateStr = facture.getDateFacture() != null ? 
            facture.getDateFacture().format(DATE_FORMATTER) : "";
        Paragraph dateText = new Paragraph(dateStr, 
            FontFactory.getFont(FontFactory.HELVETICA, 9));
        dateCell.addElement(dateText);
        infoTable.addCell(dateCell);
        
        // Ref: N°
        PdfPCell refCell = new PdfPCell();
        refCell.setBorder(Rectangle.NO_BORDER);
        refCell.setPadding(8);
        Paragraph refLabel = new Paragraph("Ref: N°", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9));
        refCell.addElement(refLabel);
        
        // Récupérer le numéro BC si disponible
        String refBC = "";
        if (facture.getBandeCommandeId() != null) {
            BandeCommande bc = bandeCommandeRepository.findById(facture.getBandeCommandeId()).orElse(null);
            if (bc != null && bc.getNumeroBC() != null) {
                refBC = bc.getNumeroBC();
            }
        }
        Paragraph refText = new Paragraph(refBC, 
            FontFactory.getFont(FontFactory.HELVETICA, 9));
        refCell.addElement(refText);
        infoTable.addCell(refCell);
        
        document.add(infoTable);
    }
    
    private void addBonDeLivraisonProductTable(Document document, FactureVente facture) throws DocumentException {
        // Tableau avec 4 colonnes au lieu de 6 (sans PU HT et Prix Total HT)
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{0.5f, 4f, 1f, 1.5f});
        table.setSpacingAfter(15);
        
        // En-têtes avec fond bleu clair et texte bleu foncé
        String[] headers = {"N° ARTICLE", "DESIGNATIONS ET PRESTATIONS", "UNITE", "QUANTITE"};
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BLUE_HEADER);
        
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(BLUE_LIGHT);
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBorderColor(Color.WHITE);
            table.addCell(cell);
        }
        
        // Lignes (sans prix)
        if (facture.getLignes() != null && !facture.getLignes().isEmpty()) {
            int lineNum = 1;
            for (var ligne : facture.getLignes()) {
                addTableCell(table, String.valueOf(lineNum++), Element.ALIGN_CENTER);
                addTableCell(table, ligne.getDesignation() != null ? ligne.getDesignation() : "", 
                    Element.ALIGN_LEFT);
                addTableCell(table, ligne.getUnite() != null ? ligne.getUnite() : "", 
                    Element.ALIGN_CENTER);
                
                // Quantité (format français avec décimales possibles)
                Double qtyValue = ligne.getQuantiteVendue() != null ? ligne.getQuantiteVendue().doubleValue() : 0.0;
                String qty = formatQuantity(qtyValue);
                addTableCell(table, qty, Element.ALIGN_RIGHT);
            }
        }
        
        document.add(table);
    }
    
    /**
     * Classe interne pour gérer le footer et le logo sur toutes les pages de la Facture Achat
     */
    private class FactureAchatFooterPageEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                PdfContentByte canvas = writer.getDirectContent();
                
                // Ajouter le logo en haut à gauche sur toutes les pages (sauf la première où il est déjà dans le header)
                if (writer.getPageNumber() > 1) {
                    addLogoToPage(canvas, writer, document);
                }
                
                // Ajouter le footer en bas de page
                PdfPTable footerTable = new PdfPTable(1);
                float tableWidth = document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin();
                footerTable.setTotalWidth(tableWidth);
                footerTable.setLockedWidth(true);
                
                PdfPCell footerCell = new PdfPCell();
                footerCell.setBackgroundColor(BLUE_LIGHT);
                footerCell.setPadding(10);
                footerCell.setBorder(Rectangle.NO_BORDER);
                footerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                
                Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
                
                Paragraph footer1 = new Paragraph("ICE: 002889872000062", footerFont);
                footer1.setAlignment(Element.ALIGN_CENTER);
                footer1.setSpacingAfter(3);
                
                Paragraph footer2 = new Paragraph("BF4 INVEST SARL au capital de 2.000.000,00 Dhs, Tel: 06 61 51 11 91", footerFont);
                footer2.setAlignment(Element.ALIGN_CENTER);
                footer2.setSpacingAfter(3);
                
                Paragraph footer3 = new Paragraph("RC de Meknes: 54287 - IF: 50499801 - TP: 17101980", footerFont);
                footer3.setAlignment(Element.ALIGN_CENTER);
                footer3.setSpacingAfter(0);
                
                footerCell.addElement(footer1);
                footerCell.addElement(footer2);
                footerCell.addElement(footer3);
                
                footerTable.addCell(footerCell);
                
                // Positionner le footer en bas de page
                float yPosition = document.bottomMargin() + 2f;
                footerTable.writeSelectedRows(0, -1, document.leftMargin(), yPosition, canvas);
            } catch (DocumentException | IOException e) {
                log.error("Error adding Facture Achat footer/logo to page", e);
            }
        }
        
        private void addLogoToPage(PdfContentByte canvas, PdfWriter writer, Document document) throws DocumentException, IOException {
            try {
                float logoWidth = 100f;
                float logoHeight = 75f;
                float xPosition = document.leftMargin();
                float yPosition = document.getPageSize().getHeight() - 2f;
                
                PdfPTable logoTable = new PdfPTable(1);
                logoTable.setTotalWidth(logoWidth);
                logoTable.setLockedWidth(true);
                
                PdfPCell logoCell = createLogoCell(writer, logoWidth, logoHeight);
                logoTable.addCell(logoCell);
                
                logoTable.writeSelectedRows(0, -1, xPosition, yPosition, canvas);
            } catch (Exception e) {
                log.error("Error adding logo to Facture Achat page", e);
            }
        }
    }
    
    // ============ FACTURE ACHAT METHODS ============
    
    private void addFactureAchatHeader(Document document, FactureAchat facture, Supplier supplier, PdfWriter writer) throws DocumentException, IOException {
        PdfPTable headerTable = new PdfPTable(3);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{2f, 4f, 4f});
        headerTable.setSpacingAfter(15);
        
        // Logo stylisé avec lignes courbes - agrandi à 100x75
        PdfPCell logoCell = createLogoCell(writer, 100f, 75f);
        headerTable.addCell(logoCell);
        
        // Espace vide
        PdfPCell emptyCell = new PdfPCell();
        emptyCell.setBorder(Rectangle.NO_BORDER);
        headerTable.addCell(emptyCell);
        
        // Fournisseur info box
        PdfPCell supplierCell = new PdfPCell();
        supplierCell.setBorder(Rectangle.BOX);
        supplierCell.setPadding(8);
        supplierCell.setBackgroundColor(Color.WHITE);
        
        Paragraph supplierLabel = new Paragraph("FOURNISSEUR:", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9));
        supplierCell.addElement(supplierLabel);
        
        String supplierName = supplier != null ? supplier.getNom() : "";
        Paragraph supplierNameText = new Paragraph(supplierName, 
            FontFactory.getFont(FontFactory.HELVETICA, 9));
        supplierCell.addElement(supplierNameText);
        
        Paragraph iceLabel = new Paragraph("ICE:", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9));
        iceLabel.setSpacingBefore(5);
        supplierCell.addElement(iceLabel);
        
        String ice = supplier != null && supplier.getIce() != null ? supplier.getIce() : "";
        Paragraph iceText = new Paragraph(ice, 
            FontFactory.getFont(FontFactory.HELVETICA, 9));
        supplierCell.addElement(iceText);
        
        headerTable.addCell(supplierCell);
        document.add(headerTable);
    }
    
    private void addFactureAchatInfo(Document document, FactureAchat facture) throws DocumentException {
        PdfPTable infoTable = new PdfPTable(3);
        infoTable.setWidthPercentage(100);
        infoTable.setSpacingAfter(15);
        
        // FACTURE N° avec fond bleu clair
        PdfPCell factNumCell = new PdfPCell();
        factNumCell.setBackgroundColor(BLUE_LIGHT);
        factNumCell.setPadding(8);
        factNumCell.setBorder(Rectangle.NO_BORDER);
        
        Paragraph factLabel = new Paragraph("FACTURE ACHAT N°", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10));
        factNumCell.addElement(factLabel);
        
        String factNum = facture.getNumeroFactureAchat() != null ? facture.getNumeroFactureAchat() : "";
        Paragraph factNumText = new Paragraph(factNum, 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
        factNumCell.addElement(factNumText);
        infoTable.addCell(factNumCell);
        
        // DU: (date)
        PdfPCell dateCell = new PdfPCell();
        dateCell.setBorder(Rectangle.NO_BORDER);
        dateCell.setPadding(8);
        Paragraph dateLabel = new Paragraph("DU:", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9));
        dateCell.addElement(dateLabel);
        
        String dateStr = facture.getDateFacture() != null ? 
            facture.getDateFacture().format(DATE_FORMATTER) : "";
        Paragraph dateText = new Paragraph(dateStr, 
            FontFactory.getFont(FontFactory.HELVETICA, 9));
        dateCell.addElement(dateText);
        infoTable.addCell(dateCell);
        
        // Ref: N° BC
        PdfPCell refCell = new PdfPCell();
        refCell.setBorder(Rectangle.NO_BORDER);
        refCell.setPadding(8);
        Paragraph refLabel = new Paragraph("Ref: N° BC", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9));
        refCell.addElement(refLabel);
        
        String refBC = "";
        if (facture.getBandeCommandeId() != null) {
            BandeCommande bc = bandeCommandeRepository.findById(facture.getBandeCommandeId()).orElse(null);
            if (bc != null && bc.getNumeroBC() != null) {
                refBC = bc.getNumeroBC();
            }
        }
        Paragraph refText = new Paragraph(refBC, 
            FontFactory.getFont(FontFactory.HELVETICA, 9));
        refCell.addElement(refText);
        infoTable.addCell(refCell);
        
        document.add(infoTable);
    }
    
    private void addFactureAchatProductTable(Document document, FactureAchat facture) throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{0.5f, 3f, 1f, 1.5f, 1.5f, 2f});
        table.setSpacingAfter(15);
        
        // En-têtes avec fond bleu clair et texte bleu foncé
        String[] headers = {"N° ARTICLE", "DESIGNATIONS", "UNITE", "QUANTITE", "PU HT", "Prix Total HT"};
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BLUE_HEADER);
        
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(BLUE_LIGHT);
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBorderColor(Color.WHITE);
            table.addCell(cell);
        }
        
        // Lignes
        if (facture.getLignes() != null && !facture.getLignes().isEmpty()) {
            int lineNum = 1;
            for (var ligne : facture.getLignes()) {
                addTableCell(table, String.valueOf(lineNum++), Element.ALIGN_CENTER);
                addTableCell(table, ligne.getDesignation() != null ? ligne.getDesignation() : "", 
                    Element.ALIGN_LEFT);
                addTableCell(table, ligne.getUnite() != null ? ligne.getUnite() : "", 
                    Element.ALIGN_CENTER);
                
                // Quantité (format français avec décimales possibles)
                Double qtyValue = ligne.getQuantiteAchetee() != null ? ligne.getQuantiteAchetee().doubleValue() : 0.0;
                String qty = formatQuantity(qtyValue);
                addTableCell(table, qty, Element.ALIGN_RIGHT);
                
                String puHT = ligne.getPrixAchatUnitaireHT() != null ? 
                    formatAmount(ligne.getPrixAchatUnitaireHT()) : "0,00";
                addTableCell(table, puHT, Element.ALIGN_RIGHT);
                
                double totalHT = (ligne.getQuantiteAchetee() != null ? ligne.getQuantiteAchetee() : 0) * 
                    (ligne.getPrixAchatUnitaireHT() != null ? ligne.getPrixAchatUnitaireHT() : 0);
                addTableCell(table, formatAmount(totalHT), Element.ALIGN_RIGHT);
            }
        }
        
        document.add(table);
    }
    
    private void addFactureAchatTotals(Document document, FactureAchat facture) throws DocumentException {
        PdfPTable totalsTable = new PdfPTable(4);
        totalsTable.setWidthPercentage(60);
        totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalsTable.setWidths(new float[]{2f, 1.5f, 1.5f, 2f});
        totalsTable.setSpacingAfter(10);
        
        double totalHT = facture.getTotalHT() != null ? facture.getTotalHT() : 0.0;
        double tauxTVA = 20.0;
        double montantTVA = facture.getTotalTVA() != null ? facture.getTotalTVA() : (totalHT * (tauxTVA / 100));
        double totalTTC = facture.getTotalTTC() != null ? facture.getTotalTTC() : (totalHT + montantTVA);
        
        addTotalsRow(totalsTable, "TOTAL HT", formatAmount(totalHT), false);
        
        // TVA A
        PdfPCell tvaLabel = new PdfPCell(new Phrase("TVA A", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        tvaLabel.setBorder(Rectangle.NO_BORDER);
        tvaLabel.setPadding(5);
        totalsTable.addCell(tvaLabel);
        
        PdfPCell tvaRate = new PdfPCell(new Phrase(String.format("%.0f%%", tauxTVA), 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        tvaRate.setBorder(Rectangle.NO_BORDER);
        tvaRate.setPadding(5);
        totalsTable.addCell(tvaRate);
        
        PdfPCell tvaAmount = new PdfPCell(new Phrase(formatAmount(montantTVA), 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        tvaAmount.setBorder(Rectangle.NO_BORDER);
        tvaAmount.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tvaAmount.setPadding(5);
        totalsTable.addCell(tvaAmount);
        
        addTotalsRow(totalsTable, "TOTAL TTC", formatAmount(totalTTC), true);
        
        document.add(totalsTable);
    }
    
    // ============ HELPER METHODS ============
    
    private void addTableCell(PdfPTable table, String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, 
            FontFactory.getFont(FontFactory.HELVETICA, 9)));
        cell.setPadding(6);
        cell.setBorder(Rectangle.BOX);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }
    
    private void addTotalsRow(PdfPTable table, String label, String value, boolean isTotal) {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, isTotal ? 11 : 10);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, isTotal ? 11 : 10);
        
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(5);
        if (isTotal) {
            // Légère marge supérieure supplémentaire pour détacher visuellement TOTAL TTC
            labelCell.setPaddingTop(10);
        }
        table.addCell(labelCell);
        
        // Cellule vide pour aligner avec les 4 colonnes (label, vide, tva%, valeur)
        PdfPCell emptyCell = new PdfPCell();
        emptyCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(emptyCell);
        
        PdfPCell emptyCell2 = new PdfPCell();
        emptyCell2.setBorder(Rectangle.NO_BORDER);
        table.addCell(emptyCell2);
        
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setPadding(5);
        if (isTotal) {
            valueCell.setPaddingTop(10);
        }
        table.addCell(valueCell);
    }
    
    private void addInfoRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(5);
        table.addCell(labelCell);
        
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        valueCell.setPadding(5);
        table.addCell(valueCell);
    }
    
    private void addInfoRowWithBackground(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBackgroundColor(BLUE_LIGHT);
        labelCell.setBorder(Rectangle.BOX);
        labelCell.setBorderColor(Color.WHITE);
        labelCell.setPadding(5);
        table.addCell(labelCell);
        
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.BOX);
        valueCell.setBorderColor(Color.WHITE);
        valueCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        valueCell.setPadding(5);
        table.addCell(valueCell);
    }
    
    /**
     * Ajoute dans une cellule les 3 lignes de footer construites à partir des informations société.
     * Ces informations sont chargées depuis {@link CompanyInfoService} pour éviter le code en dur.
     */
    private void addCompanyFooterParagraphs(PdfPCell footerCell, Font footerFont) {
        try {
            com.bf4invest.model.CompanyInfo info = companyInfoService.getCompanyInfo();

            String iceText = info.getIce() != null ? info.getIce() : "";
            String raison = info.getRaisonSociale() != null ? info.getRaisonSociale() : "";
            String capital = info.getCapital() != null ? info.getCapital() : "";
            String tel = info.getTelephone() != null ? info.getTelephone() : "";
            String ville = info.getVille() != null ? info.getVille() : "";
            String rc = info.getRc() != null ? info.getRc() : "";
            String ifFiscal = info.getIfFiscal() != null ? info.getIfFiscal() : "";
            String tp = info.getTp() != null ? info.getTp() : "";

            Paragraph footer1 = new Paragraph("ICE: " + iceText, footerFont);
            footer1.setAlignment(Element.ALIGN_CENTER);
            footer1.setSpacingAfter(3);

            String capitalLine = (raison + " au capital de " + capital + " Dhs, Tel: " + tel).trim();
            Paragraph footer2 = new Paragraph(capitalLine, footerFont);
            footer2.setAlignment(Element.ALIGN_CENTER);
            footer2.setSpacingAfter(3);

            String rcLine = ("RC de " + ville + ": " + rc + " - IF: " + ifFiscal + " - TP: " + tp).trim();
            Paragraph footer3 = new Paragraph(rcLine, footerFont);
            footer3.setAlignment(Element.ALIGN_CENTER);
            footer3.setSpacingAfter(0);

            footerCell.addElement(footer1);
            footerCell.addElement(footer2);
            footerCell.addElement(footer3);
        } catch (Exception e) {
            // En cas de problème inattendu, on ne bloque pas la génération du PDF
            log.error("Error building company footer paragraphs", e);
        }
    }
    
    private String convertAmountToFrenchWords(double amount) {
        long wholePart = (long) amount;
        int decimalPart = (int) Math.round((amount - wholePart) * 100);
        
        String wholeWords = convertNumberToFrench(wholePart);
        String decimalWords = convertNumberToFrench(decimalPart);
        
        if (wholePart == 1) {
            return wholeWords + " Dirham et " + decimalWords + " Centimes";
        } else {
            return wholeWords + " Dirhams et " + decimalWords + " Centimes";
        }
    }
    
    private String convertNumberToFrench(long number) {
        if (number == 0) return "Zéro";
        
        String[] units = {"", "Un", "Deux", "Trois", "Quatre", "Cinq", "Six", "Sept", "Huit", "Neuf",
                "Dix", "Onze", "Douze", "Treize", "Quatorze", "Quinze", "Seize", "Dix-Sept", "Dix-Huit", "Dix-Neuf"};
        String[] tens = {"", "", "Vingt", "Trente", "Quarante", "Cinquante", "Soixante", "Soixante", "Quatre-Vingt", "Quatre-Vingt"};
        
        if (number < 20) {
            return units[(int) number];
        }
        
        if (number < 100) {
            long tensPart = number / 10;
            long unitsPart = number % 10;
            
            if (tensPart == 7 || tensPart == 9) {
                // Soixante-dix, Quatre-vingt-dix
                return tens[(int) tensPart] + (tensPart == 7 ? "-Dix" : "-Dix") + 
                       (unitsPart > 0 ? "-" + units[(int) unitsPart] : "");
            }
            
            String result = tens[(int) tensPart];
            if (unitsPart == 1 && (tensPart == 2 || tensPart == 3 || tensPart == 4 || tensPart == 5 || tensPart == 6 || tensPart == 8)) {
                // "Vingt et Un", "Trente et Un", etc. (mais pas pour Soixante et Quatre-Vingt)
                result += " et " + units[(int) unitsPart];
            } else if (unitsPart > 0) {
                result += "-" + units[(int) unitsPart];
            } else if (tensPart == 8) {
                result += "s";
            }
            return result;
        }
        
        if (number < 1000) {
            long hundreds = number / 100;
            long remainder = number % 100;
            
            String result = (hundreds == 1 ? "Cent" : units[(int) hundreds] + " Cent");
            if (remainder == 0 && hundreds > 1) {
                result += "s";
            }
            if (remainder > 0) {
                result += " " + convertNumberToFrench(remainder);
            }
            return result;
        }
        
        if (number < 1000000) {
            long thousands = number / 1000;
            long remainder = number % 1000;
            
            String result = convertNumberToFrench(thousands);
            if (thousands == 1) {
                result += " Mille";
            } else {
                result += " Mille";
            }
            if (remainder > 0) {
                result += " " + convertNumberToFrench(remainder);
            }
            return result;
        }
        
        // Pour les millions (cas général simplifié)
        return String.valueOf(number);
    }
    
    // ============ DASHBOARD REPORT METHOD ============
    
    public byte[] generateDashboardReport(com.bf4invest.dto.DashboardKpiResponse kpis, 
                                          java.time.LocalDate from, 
                                          java.time.LocalDate to,
                                          Double soldeActuel) throws DocumentException, IOException {
        Document document = new Document(PageSize.A4, 40f, 40f, 60f, 60f);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        
        document.open();
        
        // Page de garde
        addReportCoverPage(document, writer, from, to);
        document.newPage();
        
        // Synthèse exécutive
        addExecutiveSummary(document, kpis);
        
        // Situation de trésorerie (NOUVEAU)
        addSoldeSection(document, soldeActuel);
        
        // Analyse TVA
        addTvaAnalysis(document, kpis);
        
        // Situation des impayés
        addImpayesSection(document, kpis);
        
        // Évolution mensuelle du CA (AMÉLIORÉ avec graphiques)
        addMonthlyCaEvolution(document, kpis);
        
        // Top Clients
        addTopClients(document, kpis);
        
        // Top Fournisseurs
        addTopSuppliers(document, kpis);
        
        // Alertes et actions requises
        addAlertsSection(document, kpis);
        
        // Footer
        addReportFooter(document);
        
        document.close();
        return baos.toByteArray();
    }
    
    private void addReportCoverPage(Document document, PdfWriter writer, 
                                    java.time.LocalDate from, java.time.LocalDate to) 
            throws DocumentException, IOException {
        // Logo
        PdfPTable logoTable = new PdfPTable(1);
        logoTable.setWidthPercentage(100);
        PdfPCell logoCell = createLogoCell(writer, 150f, 80f);
        logoCell.setPaddingBottom(20f);
        logoTable.addCell(logoCell);
        document.add(logoTable);
        
        // Titre principal
        Paragraph title = new Paragraph("RAPPORT D'ACTIVITÉ", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 28, Color.BLACK));
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(30f);
        document.add(title);
        
        // Période
        String periodStr = "Période: ";
        if (from != null && to != null) {
            periodStr += from.format(DATE_FORMATTER) + " au " + to.format(DATE_FORMATTER);
        } else if (from != null) {
            periodStr += "À partir du " + from.format(DATE_FORMATTER);
        } else if (to != null) {
            periodStr += "Jusqu'au " + to.format(DATE_FORMATTER);
        } else {
            periodStr += "Toutes périodes confondues";
        }
        
        Paragraph period = new Paragraph(periodStr, 
            FontFactory.getFont(FontFactory.HELVETICA, 14, Color.DARK_GRAY));
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(10f);
        document.add(period);
        
        // Date de génération
        String generatedDate = "Généré le: " + java.time.LocalDate.now().format(DATE_FORMATTER) + 
                               " à " + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        Paragraph generated = new Paragraph(generatedDate, 
            FontFactory.getFont(FontFactory.HELVETICA, 12, Color.GRAY));
        generated.setAlignment(Element.ALIGN_CENTER);
        generated.setSpacingAfter(50f);
        document.add(generated);
    }
    
    private void addExecutiveSummary(Document document, com.bf4invest.dto.DashboardKpiResponse kpis) 
            throws DocumentException {
        Paragraph sectionTitle = new Paragraph("1. SYNTHÈSE EXÉCUTIVE", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        PdfPTable summaryTable = new PdfPTable(2);
        summaryTable.setWidthPercentage(100);
        summaryTable.setWidths(new float[]{2.5f, 2.5f});
        summaryTable.setSpacingAfter(10f);
        
        // Utiliser des lignes alternées pour une meilleure lisibilité
        boolean isEven = false;
        addSummaryRowStyled(summaryTable, "Chiffre d'Affaires HT:", formatCurrency(kpis.getCaHT()), isEven);
        isEven = !isEven;
        addSummaryRowStyled(summaryTable, "Chiffre d'Affaires TTC:", formatCurrency(kpis.getCaTTC()), isEven);
        isEven = !isEven;
        addSummaryRowStyled(summaryTable, "Total Achats HT:", formatCurrency(kpis.getTotalAchatsHT()), isEven);
        isEven = !isEven;
        addSummaryRowStyled(summaryTable, "Total Achats TTC:", formatCurrency(kpis.getTotalAchatsTTC()), isEven);
        isEven = !isEven;
        addSummaryRowStyled(summaryTable, "Marge Nette:", formatCurrency(kpis.getMargeTotale()), isEven);
        isEven = !isEven;
        addSummaryRowStyled(summaryTable, "Marge Moyenne:", String.format("%.2f%%", kpis.getMargeMoyenne()), isEven);
        
        document.add(summaryTable);
        document.add(new Paragraph(" ")); // Espacement
    }
    
    private void addSoldeSection(Document document, Double soldeActuel) throws DocumentException {
        Paragraph sectionTitle = new Paragraph("2. SITUATION DE TRÉSORERIE", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        // Tableau avec fond coloré selon le solde
        PdfPTable soldeTable = new PdfPTable(2);
        soldeTable.setWidthPercentage(100);
        soldeTable.setWidths(new float[]{2f, 3f});
        soldeTable.setSpacingAfter(15f);
        
        PdfPCell labelCell = new PdfPCell(new Paragraph("Solde Global Actuel:", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
        labelCell.setBorder(Rectangle.BOX);
        labelCell.setBorderColor(Color.GRAY);
        labelCell.setPadding(15f);
        labelCell.setBackgroundColor(BLUE_LIGHT);
        soldeTable.addCell(labelCell);
        
        PdfPCell valueCell = new PdfPCell(new Paragraph(formatCurrency(soldeActuel != null ? soldeActuel : 0.0), 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, 
                (soldeActuel != null && soldeActuel >= 0) ? new Color(0, 128, 0) : RED)));
        valueCell.setBorder(Rectangle.BOX);
        valueCell.setBorderColor(Color.GRAY);
        valueCell.setPadding(15f);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setBackgroundColor((soldeActuel != null && soldeActuel >= 0) ? 
            new Color(240, 255, 240) : new Color(255, 240, 240));
        soldeTable.addCell(valueCell);
        
        document.add(soldeTable);
        
        // Indicateur d'état
        Paragraph status = new Paragraph(
            (soldeActuel != null && soldeActuel >= 0) ? 
                "✓ Trésorerie excédentaire" : "⚠ Trésorerie déficitaire",
            FontFactory.getFont(FontFactory.HELVETICA, 11, 
                (soldeActuel != null && soldeActuel >= 0) ? new Color(0, 128, 0) : RED));
        status.setSpacingBefore(5f);
        status.setSpacingAfter(15f);
        document.add(status);
        
        document.add(new Paragraph(" ")); // Espacement
    }
    
    private void addTvaAnalysis(Document document, com.bf4invest.dto.DashboardKpiResponse kpis) 
            throws DocumentException {
        Paragraph sectionTitle = new Paragraph("3. ANALYSE TVA", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        PdfPTable tvaTable = new PdfPTable(2);
        tvaTable.setWidthPercentage(100);
        tvaTable.setWidths(new float[]{2.5f, 2.5f});
        tvaTable.setSpacingAfter(10f);
        
        addSummaryRowStyled(tvaTable, "TVA Collectée:", formatCurrency(kpis.getTvaCollectee()), false);
        addSummaryRowStyled(tvaTable, "TVA Déductible:", formatCurrency(kpis.getTvaDeductible()), true);
        
        double soldeTva = kpis.getTvaCollectee() - kpis.getTvaDeductible();
        PdfPCell labelCell = new PdfPCell(new Paragraph("Solde TVA:", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
        labelCell.setBorder(Rectangle.BOX);
        labelCell.setBorderColor(Color.GRAY);
        labelCell.setPadding(12f);
        labelCell.setBackgroundColor(BLUE_LIGHT);
        tvaTable.addCell(labelCell);
        
        PdfPCell valueCell = new PdfPCell(new Paragraph(formatCurrency(soldeTva), 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, 
                soldeTva < 0 ? RED : new Color(0, 128, 0))));
        valueCell.setBorder(Rectangle.BOX);
        valueCell.setBorderColor(Color.GRAY);
        valueCell.setPadding(12f);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (soldeTva < 0) {
            valueCell.setBackgroundColor(new Color(255, 240, 240)); // Rouge clair si négatif
        } else {
            valueCell.setBackgroundColor(new Color(240, 255, 240)); // Vert clair si positif
        }
        tvaTable.addCell(valueCell);
        
        document.add(tvaTable);
        document.add(new Paragraph(" "));
    }
    
    private void addImpayesSection(Document document, com.bf4invest.dto.DashboardKpiResponse kpis) 
            throws DocumentException {
        if (kpis.getImpayes() == null) return;
        
        Paragraph sectionTitle = new Paragraph("4. SITUATION DES IMPAYÉS", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        PdfPTable impayesTable = new PdfPTable(3);
        impayesTable.setWidthPercentage(100);
        impayesTable.setWidths(new float[]{2.5f, 2f, 1f});
        impayesTable.setSpacingAfter(10f);
        
        // Header
        addTableHeaderCell(impayesTable, "Tranche", true);
        addTableHeaderCell(impayesTable, "Montant", true);
        addTableHeaderCell(impayesTable, "%", true);
        
        double totalImpayes = kpis.getImpayes().getTotalImpayes();
        double impayes0_30 = kpis.getImpayes().getImpayes0_30();
        double impayes31_60 = kpis.getImpayes().getImpayes31_60();
        double impayesPlus60 = kpis.getImpayes().getImpayesPlus60();
        
        // Rows avec lignes alternées
        addImpayesRowStyled(impayesTable, "0-30 jours", impayes0_30, totalImpayes, false);
        addImpayesRowStyled(impayesTable, "31-60 jours", impayes31_60, totalImpayes, true);
        addImpayesRowStyled(impayesTable, "60+ jours", impayesPlus60, totalImpayes, false);
        
        // Total row
        PdfPCell totalLabelCell = new PdfPCell(new Paragraph("TOTAL", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
        totalLabelCell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        totalLabelCell.setPadding(8f);
        totalLabelCell.setBackgroundColor(BLUE_LIGHT);
        impayesTable.addCell(totalLabelCell);
        
        PdfPCell totalAmountCell = new PdfPCell(new Paragraph(formatCurrency(totalImpayes), 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
        totalAmountCell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        totalAmountCell.setPadding(8f);
        totalAmountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalAmountCell.setBackgroundColor(BLUE_LIGHT);
        impayesTable.addCell(totalAmountCell);
        
        PdfPCell totalPercentCell = new PdfPCell(new Paragraph("100%", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
        totalPercentCell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        totalPercentCell.setPadding(8f);
        totalPercentCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        totalPercentCell.setBackgroundColor(BLUE_LIGHT);
        impayesTable.addCell(totalPercentCell);
        
        document.add(impayesTable);
        document.add(new Paragraph(" "));
    }
    
    private void addMonthlyCaEvolution(Document document, com.bf4invest.dto.DashboardKpiResponse kpis) 
            throws DocumentException {
        if (kpis.getCaMensuel() == null || kpis.getCaMensuel().isEmpty()) return;
        
        Paragraph sectionTitle = new Paragraph("5. ÉVOLUTION MENSUELLE DU CA", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        // Limit to last 12 months
        List<com.bf4invest.dto.DashboardKpiResponse.MonthlyData> monthlyData = kpis.getCaMensuel();
        if (monthlyData.size() > 12) {
            monthlyData = monthlyData.subList(monthlyData.size() - 12, monthlyData.size());
        }
        
        // Calculer le maximum pour la normalisation des barres
        double maxCA = monthlyData.stream()
            .mapToDouble(com.bf4invest.dto.DashboardKpiResponse.MonthlyData::getCaHT)
            .max()
            .orElse(1.0);
        
        PdfPTable monthlyTable = new PdfPTable(4);
        monthlyTable.setWidthPercentage(100);
        monthlyTable.setWidths(new float[]{1.5f, 2f, 1.5f, 1f});
        monthlyTable.setSpacingAfter(10f);
        
        // Header
        addTableHeaderCell(monthlyTable, "Mois", true);
        addTableHeaderCell(monthlyTable, "Évolution", true);
        addTableHeaderCell(monthlyTable, "CA HT", true);
        addTableHeaderCell(monthlyTable, "Marge %", true);
        
        // Rows avec graphiques à barres
        boolean isEven = false;
        for (com.bf4invest.dto.DashboardKpiResponse.MonthlyData month : monthlyData) {
            // Mois
            PdfPCell monthCell = new PdfPCell(new Paragraph(formatMonth(month.getMois()), 
                FontFactory.getFont(FontFactory.HELVETICA, 10)));
            monthCell.setBorder(Rectangle.BOX);
            monthCell.setBorderColor(Color.GRAY);
            monthCell.setPadding(8f);
            monthCell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
            monthlyTable.addCell(monthCell);
            
            // Barre graphique
            PdfPCell barCell = createBarChartCell(month.getCaHT(), maxCA, isEven);
            monthlyTable.addCell(barCell);
            
            // CA HT
            PdfPCell caCell = new PdfPCell(new Paragraph(formatCurrency(month.getCaHT()), 
                FontFactory.getFont(FontFactory.HELVETICA, 10)));
            caCell.setBorder(Rectangle.BOX);
            caCell.setBorderColor(Color.GRAY);
            caCell.setPadding(8f);
            caCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            caCell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
            monthlyTable.addCell(caCell);
            
            // Marge %
            PdfPCell margeCell = new PdfPCell(new Paragraph(String.format("%.1f%%", month.getMarge()), 
                FontFactory.getFont(FontFactory.HELVETICA, 10)));
            margeCell.setBorder(Rectangle.BOX);
            margeCell.setBorderColor(Color.GRAY);
            margeCell.setPadding(8f);
            margeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            margeCell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
            monthlyTable.addCell(margeCell);
            
            isEven = !isEven;
        }
        
        document.add(monthlyTable);
        document.add(new Paragraph(" "));
    }
    
    private PdfPCell createBarChartCell(double value, double maxValue, boolean isEven) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(Color.GRAY);
        cell.setPadding(5f);
        cell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
        cell.setFixedHeight(25f);
        
        // Créer une table imbriquée pour la barre
        PdfPTable barTable = new PdfPTable(2);
        barTable.setWidthPercentage(100);
        barTable.setSpacingBefore(0f);
        barTable.setSpacingAfter(0f);
        
        double percentage = maxValue > 0 ? (value / maxValue) * 100 : 0;
        float barWidth = (float) Math.max(Math.min(percentage, 98), 2); // Entre 2% et 98% pour visibilité
        float emptyWidth = 100f - barWidth;
        
        // Cellule avec la barre colorée
        PdfPCell barColorCell = new PdfPCell();
        barColorCell.setBorder(Rectangle.NO_BORDER);
        barColorCell.setBackgroundColor(BLUE_HEADER);
        barColorCell.setFixedHeight(15f);
        barColorCell.setPadding(0f);
        barTable.addCell(barColorCell);
        
        // Cellule vide pour le reste
        PdfPCell emptyCell = new PdfPCell();
        emptyCell.setBorder(Rectangle.NO_BORDER);
        emptyCell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
        emptyCell.setFixedHeight(15f);
        emptyCell.setPadding(0f);
        barTable.addCell(emptyCell);
        
        // Ajuster les largeurs proportionnellement
        barTable.setWidths(new float[]{barWidth, emptyWidth});
        
        cell.addElement(barTable);
        return cell;
    }
    
    private void addTopClients(Document document, com.bf4invest.dto.DashboardKpiResponse kpis) 
            throws DocumentException {
        if (kpis.getTopClients() == null || kpis.getTopClients().isEmpty()) return;
        
        Paragraph sectionTitle = new Paragraph("6. TOP 10 CLIENTS", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        PdfPTable clientsTable = new PdfPTable(2);
        clientsTable.setWidthPercentage(100);
        clientsTable.setWidths(new float[]{3f, 2f});
        clientsTable.setSpacingAfter(10f);
        
        // Header
        addTableHeaderCell(clientsTable, "Client", true);
        addTableHeaderCell(clientsTable, "Montant", true);
        
        // Limit to top 10
        List<com.bf4invest.dto.DashboardKpiResponse.FournisseurClientStat> topClients = kpis.getTopClients();
        if (topClients.size() > 10) {
            topClients = topClients.subList(0, 10);
        }
        
        // Rows avec lignes alternées
        boolean isEven = false;
        for (com.bf4invest.dto.DashboardKpiResponse.FournisseurClientStat client : topClients) {
            addTableCellStyled(clientsTable, client.getNom(), false, isEven);
            addTableCellStyled(clientsTable, formatCurrency(client.getMontant()), true, isEven);
            isEven = !isEven;
        }
        
        document.add(clientsTable);
        document.add(new Paragraph(" "));
    }
    
    private void addTopSuppliers(Document document, com.bf4invest.dto.DashboardKpiResponse kpis) 
            throws DocumentException {
        if (kpis.getTopFournisseurs() == null || kpis.getTopFournisseurs().isEmpty()) return;
        
        Paragraph sectionTitle = new Paragraph("7. TOP 10 FOURNISSEURS", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BLUE_DARK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        PdfPTable suppliersTable = new PdfPTable(2);
        suppliersTable.setWidthPercentage(100);
        suppliersTable.setWidths(new float[]{3f, 2f});
        suppliersTable.setSpacingAfter(10f);
        
        // Header
        addTableHeaderCell(suppliersTable, "Fournisseur", true);
        addTableHeaderCell(suppliersTable, "Montant", true);
        
        // Limit to top 10
        List<com.bf4invest.dto.DashboardKpiResponse.FournisseurClientStat> topSuppliers = kpis.getTopFournisseurs();
        if (topSuppliers.size() > 10) {
            topSuppliers = topSuppliers.subList(0, 10);
        }
        
        // Rows avec lignes alternées
        boolean isEven = false;
        for (com.bf4invest.dto.DashboardKpiResponse.FournisseurClientStat supplier : topSuppliers) {
            addTableCellStyled(suppliersTable, supplier.getNom(), false, isEven);
            addTableCellStyled(suppliersTable, formatCurrency(supplier.getMontant()), true, isEven);
            isEven = !isEven;
        }
        
        document.add(suppliersTable);
        document.add(new Paragraph(" "));
    }
    
    private void addAlertsSection(Document document, com.bf4invest.dto.DashboardKpiResponse kpis) 
            throws DocumentException {
        Paragraph sectionTitle = new Paragraph("8. ALERTES ET ACTIONS REQUISES", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, RED));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(20f);
        document.add(sectionTitle);
        
        PdfPTable alertsTable = new PdfPTable(2);
        alertsTable.setWidthPercentage(100);
        alertsTable.setWidths(new float[]{2.5f, 2.5f});
        alertsTable.setSpacingAfter(10f);
        
        addSummaryRowStyled(alertsTable, "Factures en retard:", 
            String.valueOf(kpis.getFacturesEnRetard()), false);
        
        if (kpis.getImpayes() != null) {
            addSummaryRowStyled(alertsTable, "Montant total impayé:", 
                formatCurrency(kpis.getImpayes().getTotalImpayes()), true);
        }
        
        document.add(alertsTable);
    }
    
    private void addReportFooter(Document document) throws DocumentException {
        Paragraph footer = new Paragraph("BF4 INVEST - Rapport généré automatiquement", 
            FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY));
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(30f);
        document.add(footer);
    }
    
    // Helper methods for report generation
    private void addSummaryRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Paragraph(label, 
            FontFactory.getFont(FontFactory.HELVETICA, 11)));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(8f);
        table.addCell(labelCell);
        
        PdfPCell valueCell = new PdfPCell(new Paragraph(value, 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(8f);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }
    
    private void addSummaryRowStyled(PdfPTable table, String label, String value, boolean isEven) {
        PdfPCell labelCell = new PdfPCell(new Paragraph(label, 
            FontFactory.getFont(FontFactory.HELVETICA, 11)));
        labelCell.setBorder(Rectangle.BOX);
        labelCell.setBorderColor(Color.GRAY);
        labelCell.setPadding(10f);
        labelCell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
        table.addCell(labelCell);
        
        PdfPCell valueCell = new PdfPCell(new Paragraph(value, 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
        valueCell.setBorder(Rectangle.BOX);
        valueCell.setBorderColor(Color.GRAY);
        valueCell.setPadding(10f);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
        table.addCell(valueCell);
    }
    
    private void addTableHeaderCell(PdfPTable table, String text, boolean bold) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, 
            FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, 11, Color.WHITE)));
        cell.setBackgroundColor(BLUE_DARK);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(Color.GRAY);
        cell.setPadding(10f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }
    
    private void addTableCell(PdfPTable table, String text, boolean rightAlign) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, 
            FontFactory.getFont(FontFactory.HELVETICA, 10)));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(Color.GRAY);
        cell.setPadding(8f);
        cell.setHorizontalAlignment(rightAlign ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        table.addCell(cell);
    }
    
    private void addTableCellStyled(PdfPTable table, String text, boolean rightAlign, boolean isEven) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, 
            FontFactory.getFont(FontFactory.HELVETICA, 10)));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(Color.GRAY);
        cell.setPadding(8f);
        cell.setHorizontalAlignment(rightAlign ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        cell.setBackgroundColor(isEven ? new Color(250, 250, 250) : Color.WHITE);
        table.addCell(cell);
    }
    
    private void addImpayesRow(PdfPTable table, String tranche, double montant, double total) {
        addTableCell(table, tranche, false);
        addTableCell(table, formatCurrency(montant), true);
        double percent = total > 0 ? (montant / total) * 100 : 0;
        addTableCell(table, String.format("%.1f%%", percent), true);
    }
    
    private void addImpayesRowStyled(PdfPTable table, String tranche, double montant, double total, boolean isEven) {
        addTableCellStyled(table, tranche, false, isEven);
        addTableCellStyled(table, formatCurrency(montant), true, isEven);
        double percent = total > 0 ? (montant / total) * 100 : 0;
        addTableCellStyled(table, String.format("%.1f%%", percent), true, isEven);
    }
    
    private String formatCurrency(double amount) {
        return formatAmount(amount) + " MAD";
    }
    
    private String formatMonth(String monthStr) {
        try {
            java.time.YearMonth ym = java.time.YearMonth.parse(monthStr);
            return ym.format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", Locale.FRENCH));
        } catch (Exception e) {
            return monthStr;
        }
    }
    
    // ============ ORDRE DE VIREMENT METHODS ============
    
    public byte[] generateOrdreVirement(OrdreVirement ov) throws DocumentException, IOException {
        Document document = new Document(PageSize.A4, 40f, 40f, 80f, 70f);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        
        // Page event pour footer et logo sur toutes les pages
        writer.setPageEvent(new OrdreVirementFooterPageEvent());
        
        document.open();
        
        // Récupérer les informations de l'entreprise
        com.bf4invest.model.CompanyInfo companyInfo = companyInfoService.getCompanyInfo();
        
        // Header avec logo et banque
        addOrdreVirementHeader(document, ov, companyInfo, writer);
        
        // Titre "ORDRE DE VIREMENT"
        addOrdreVirementTitle(document, ov);
        
        // Informations du donneur d'ordre
        addOrdreVirementDonorInfo(document, companyInfo);
        
        // Montant en lettres
        addOrdreVirementAmountInWords(document, ov);
        
        // Détails du virement
        addOrdreVirementDetails(document, ov);
        
        // Signature
        addOrdreVirementSignature(document);
        
        document.close();
        return baos.toByteArray();
    }
    
    private void addOrdreVirementHeader(Document document, OrdreVirement ov, 
                                       com.bf4invest.model.CompanyInfo companyInfo, 
                                       PdfWriter writer) throws DocumentException, IOException {
        // Objectif: bloc banque CENTRÉ comme sur le modèle (logo à gauche, texte banque centré)
        PdfPTable headerTable = new PdfPTable(3);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{2.2f, 5.6f, 2.2f});
        headerTable.setSpacingAfter(10);
        
        // Logo à gauche (comme modèle) - collé en haut
        PdfPCell logoCell = createLogoCell(writer, 100f, 75f);
        logoCell.setVerticalAlignment(Element.ALIGN_TOP);
        logoCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        logoCell.setPaddingTop(0);
        logoCell.setPaddingBottom(0);
        headerTable.addCell(logoCell);
        
        // Bloc banque centré - positionné plus bas que le logo
        PdfPCell bankCell = new PdfPCell();
        bankCell.setBorder(Rectangle.NO_BORDER);
        // Descendre significativement le bloc banque pour que le logo soit visuellement plus haut (comme le modèle)
        bankCell.setPaddingTop(35);
        bankCell.setPaddingBottom(0);
        bankCell.setPaddingLeft(0);
        bankCell.setPaddingRight(0);
        bankCell.setVerticalAlignment(Element.ALIGN_TOP);
        bankCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        
        Font bankFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);
        
        Paragraph bankLine1 = new Paragraph("A Mr Le Directeur", bankFont);
        bankLine1.setAlignment(Element.ALIGN_CENTER);
        bankLine1.setSpacingAfter(2);
        
        String banque = companyInfo.getBanque() != null ? companyInfo.getBanque() : "";
        Paragraph bankLine2 = new Paragraph(banque, bankFont);
        bankLine2.setAlignment(Element.ALIGN_CENTER);
        bankLine2.setSpacingAfter(2);
        
        String agence = companyInfo.getAgence() != null ? companyInfo.getAgence() : "";
        Paragraph bankLine3 = new Paragraph(agence, bankFont);
        bankLine3.setAlignment(Element.ALIGN_CENTER);
        
        bankCell.addElement(bankLine1);
        bankCell.addElement(bankLine2);
        bankCell.addElement(bankLine3);
        
        headerTable.addCell(bankCell);
        
        // Colonne droite vide (pour centrer visuellement le bloc banque)
        PdfPCell emptyRight = new PdfPCell();
        emptyRight.setBorder(Rectangle.NO_BORDER);
        headerTable.addCell(emptyRight);
        
        document.add(headerTable);
        
        // Petit espace comme sur le modèle avant le titre
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(6);
        document.add(spacer);
    }
    
    private void addOrdreVirementTitle(Document document, OrdreVirement ov) throws DocumentException {
        // Titre "ORDRE DE VIREMENT" en bleu, souligné, centré
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BLUE_DARK);
        titleFont.setStyle(Font.UNDERLINE);
        Paragraph title = new Paragraph("ORDRE DE VIREMENT", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(0);
        document.add(title);
        
        // Si EXPRESS, ajouter "VIREMENT EXPRESS" en rouge, souligné, centré
        if ("EXPRESS".equals(ov.getType())) {
            Font expressFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, RED);
            expressFont.setStyle(Font.UNDERLINE);
            Paragraph expressTitle = new Paragraph("VIREMENT EXPRESS", expressFont);
            expressTitle.setAlignment(Element.ALIGN_CENTER);
            expressTitle.setSpacingAfter(0);
            document.add(expressTitle);
        }
        
        // Grand espace vertical (comme le modèle) avant le bloc donneur d'ordre
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(60);
        document.add(spacer);
    }
    
    private void addOrdreVirementDonorInfo(Document document, com.bf4invest.model.CompanyInfo companyInfo) throws DocumentException {
        // Dans le modèle: ligne plutôt "noire" avec nom en bleu
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
        Font blueBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BLUE_DARK);
        
        // "NOM DU DONNEUR D'ORDRE : STE BF4 INVEST"
        String raisonSociale = companyInfo.getRaisonSociale() != null ? companyInfo.getRaisonSociale() : "BF4 INVEST SARL";
        Paragraph donorLine = new Paragraph();
        donorLine.add(new Chunk("NOM DU DONNEUR D'ORDRE : ", labelFont));
        donorLine.add(new Chunk(raisonSociale, blueBoldFont));
        donorLine.setSpacingAfter(6);
        document.add(donorLine);
        
        // "Veuillez virer par le débit de notre compte N° [RIB]"
        String rib = companyInfo.getRib() != null ? companyInfo.getRib() : "";
        Paragraph accountLine = new Paragraph();
        accountLine.add(new Chunk("Veuillez virer par le débit de notre compte N° ", labelFont));
        accountLine.add(new Chunk(rib, blueBoldFont));
        accountLine.setSpacingAfter(30);
        document.add(accountLine);
    }
    
    private String formatOvWordsWithSpaces(String rawWords) {
        if (rawWords == null) return "";
        // Remplacer les tirets par des espaces, puis restaurer "Quatre-Vingt" comme sur le modèle
        String s = rawWords.replace("-", " ");
        s = s.replaceAll("\\s+", " ").trim();
        // Rétablir l'orthographe attendue dans l'image: "Quatre-Vingt"
        s = s.replace("Quatre Vingt", "Quatre-Vingt");
        return s;
    }
    
    private void addOrdreVirementAmountInWords(Document document, OrdreVirement ov) throws DocumentException {
        // "La somme de : [Montant en lettres]" - centré, bleu, comme le modèle
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);
        Font blueBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, BLUE_DARK);
        
        // Pour les ordres de virement, on utilise seulement la partie entière sans centimes
        Double montant = ov.getMontant();
        if (montant == null) {
            montant = 0.0;
        }
        long wholePart = montant.longValue();
        String wholeWords = formatOvWordsWithSpaces(convertNumberToFrench(wholePart));
        String amountInWords = wholeWords + (wholePart == 1 ? " Dirham" : " Dirhams");
        
        Paragraph amountLine = new Paragraph();
        amountLine.add(new Chunk("La somme de : ", labelFont));
        amountLine.add(new Chunk(amountInWords, blueBoldFont));
        amountLine.setAlignment(Element.ALIGN_CENTER);
        amountLine.setSpacingAfter(35);
        document.add(amountLine);
    }
    
    private void addOrdreVirementDetails(Document document, OrdreVirement ov) throws DocumentException {
        // Utiliser une table 3 colonnes pour verrouiller les alignements (Montant / DHS. / blocs)
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
        Font valueBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
        Font blueValueBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BLUE_DARK);
        Font blueAmountBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BLUE_DARK);
        Font redValueBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, RED);
        
        boolean isExpress = "EXPRESS".equals(ov.getType());
        
        // Table 4 colonnes pour reproduire l'espacement du modèle:
        // [label] [valeur] [DHS.] [espace vide]
        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{2.2f, 3.0f, 1.0f, 3.8f});
        t.setSpacingAfter(0);
        
        // Helpers
        java.util.function.BiFunction<String, Font, PdfPCell> cell = (txt, font) -> {
            PdfPCell c = new PdfPCell(new Phrase(txt != null ? txt : "", font));
            c.setBorder(Rectangle.NO_BORDER);
            c.setPaddingTop(6);
            c.setPaddingBottom(6);
            c.setPaddingLeft(0);
            c.setPaddingRight(0);
            c.setVerticalAlignment(Element.ALIGN_MIDDLE);
            return c;
        };
        java.util.function.BiFunction<String, Font, PdfPCell> valueCell = (txt, font) -> {
            PdfPCell c = cell.apply(txt, font);
            // Laisser un petit "air" comme dans l'image (valeur collée mais lisible)
            c.setPaddingLeft(6);
            return c;
        };
        
        // Montant row: label | amount | DHS. | (vide)
        String amount = formatAmount(ov.getMontant());
        PdfPCell c1 = cell.apply("Montant :", labelFont);
        PdfPCell c2 = valueCell.apply(amount, blueAmountBold);
        PdfPCell c3 = valueCell.apply("DHS.", valueBold);
        PdfPCell c4 = cell.apply("", valueBold);
        c1.setHorizontalAlignment(Element.ALIGN_LEFT);
        // Dans le modèle, le montant commence juste après le label (pas centré)
        c2.setHorizontalAlignment(Element.ALIGN_LEFT);
        // "DHS." proche du montant
        c3.setHorizontalAlignment(Element.ALIGN_LEFT);
        c4.setHorizontalAlignment(Element.ALIGN_LEFT);
        t.addCell(c1);
        t.addCell(c2);
        t.addCell(c3);
        t.addCell(c4);
        
        // En faveur de
        String nomBeneficiaire = ov.getNomBeneficiaire() != null ? ov.getNomBeneficiaire() : "";
        Font nomFont = isExpress ? redValueBold : valueBold;
        PdfPCell b1 = cell.apply("En faveur de :", labelFont);
        PdfPCell b2 = valueCell.apply(nomBeneficiaire, nomFont);
        PdfPCell b3 = cell.apply("", valueBold);
        PdfPCell b4 = cell.apply("", valueBold);
        b1.setHorizontalAlignment(Element.ALIGN_LEFT);
        b2.setHorizontalAlignment(Element.ALIGN_LEFT);
        b3.setHorizontalAlignment(Element.ALIGN_LEFT);
        b4.setHorizontalAlignment(Element.ALIGN_LEFT);
        t.addCell(b1);
        // Valeur sur toute la largeur restante (comme modèle)
        b2.setColspan(3);
        t.addCell(b2);
        
        // Domicilié chez
        String banqueBeneficiaire = ov.getBanqueBeneficiaire() != null ? ov.getBanqueBeneficiaire() : "";
        PdfPCell d1 = cell.apply("Domicilié chez :", labelFont);
        PdfPCell d2 = valueCell.apply(banqueBeneficiaire, blueValueBold);
        PdfPCell d3 = cell.apply("", valueBold);
        PdfPCell d4 = cell.apply("", valueBold);
        d1.setHorizontalAlignment(Element.ALIGN_LEFT);
        d2.setHorizontalAlignment(Element.ALIGN_LEFT);
        t.addCell(d1);
        d2.setColspan(3);
        t.addCell(d2);
        
        // Compte n°
        String rib = ov.getRibBeneficiaire() != null ? ov.getRibBeneficiaire() : "";
        PdfPCell r1 = cell.apply("Compte n° :", labelFont);
        PdfPCell r2 = valueCell.apply(rib, blueValueBold);
        PdfPCell r3 = cell.apply("", valueBold);
        PdfPCell r4 = cell.apply("", valueBold);
        r1.setHorizontalAlignment(Element.ALIGN_LEFT);
        r2.setHorizontalAlignment(Element.ALIGN_LEFT);
        t.addCell(r1);
        r2.setColspan(3);
        t.addCell(r2);
        
        // Motif
        String motif = ov.getMotif() != null ? ov.getMotif() : "";
        Font motifFont = isExpress ? redValueBold : valueBold;
        PdfPCell m1 = cell.apply("Motif :", labelFont);
        PdfPCell m2 = valueCell.apply(motif, motifFont);
        PdfPCell m3 = cell.apply("", valueBold);
        PdfPCell m4 = cell.apply("", valueBold);
        m1.setHorizontalAlignment(Element.ALIGN_LEFT);
        m2.setHorizontalAlignment(Element.ALIGN_LEFT);
        t.addCell(m1);
        m2.setColspan(3);
        t.addCell(m2);
        
        document.add(t);
        
        // Espace avant signature (comme modèle)
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(55);
        document.add(spacer);
    }
    
    private void addOrdreVirementSignature(Document document) throws DocumentException {
        Font signatureFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
        PdfPTable sig = new PdfPTable(1);
        // Même largeur/centrage que le footer (82% de la zone dispo) pour aligner la fin du texte avec le cadre du footer
        sig.setWidthPercentage(82);
        sig.setHorizontalAlignment(Element.ALIGN_CENTER);
        PdfPCell c = new PdfPCell(new Phrase("Signature du donneur d'ordre", signatureFont));
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setPaddingRight(0);
        c.setPaddingTop(0);
        c.setPaddingBottom(0);
        sig.addCell(c);
        sig.setSpacingAfter(0);
        document.add(sig);
    }
    
    // Page event pour footer et logo sur toutes les pages
    private class OrdreVirementFooterPageEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                // Footer avec informations société
                addOrdreVirementFooter(writer, document);
                
                // Logo sur les pages suivantes (page > 1)
                if (writer.getPageNumber() > 1) {
                    addLogoToPage(writer, document);
                }
            } catch (Exception e) {
                log.error("Error in OrdreVirementFooterPageEvent", e);
            }
        }
        
        private void addOrdreVirementFooter(PdfWriter writer, Document document) throws DocumentException {
            PdfContentByte canvas = writer.getDirectContent();
            float yPosition = document.bottomMargin() + 2f;
            
            // Récupérer les informations société
            com.bf4invest.model.CompanyInfo info = companyInfoService.getCompanyInfo();
            
            // Créer un tableau pour le footer avec bordure (plus étroit et centré, fond bleu clair)
            float availableWidth = document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin();
            float footerWidth = availableWidth * 0.82f;
            float xPosition = (document.getPageSize().getWidth() - footerWidth) / 2f;
            
            PdfPTable footerTable = new PdfPTable(1);
            footerTable.setTotalWidth(footerWidth);
            footerTable.setLockedWidth(true);
            
            PdfPCell footerCell = new PdfPCell();
            footerCell.setBorder(Rectangle.BOX);
            footerCell.setBorderColor(Color.BLACK);
            footerCell.setBackgroundColor(BLUE_LIGHT);
            footerCell.setPadding(6);
            footerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            
            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, Color.BLACK);
            
            String raison = info.getRaisonSociale() != null ? info.getRaisonSociale() : "";
            String capital = info.getCapital() != null ? info.getCapital() : "";
            String tel = info.getTelephone() != null ? info.getTelephone() : "";
            String ville = info.getVille() != null ? info.getVille() : "";
            String rc = info.getRc() != null ? info.getRc() : "";
            String ifFiscal = info.getIfFiscal() != null ? info.getIfFiscal() : "";
            String tp = info.getTp() != null ? info.getTp() : "";
            
            Paragraph footer1 = new Paragraph(raison + " au capital de " + capital + " Dhs,  Tél : " + tel, footerFont);
            footer1.setAlignment(Element.ALIGN_CENTER);
            footer1.setSpacingAfter(2);
            
            Paragraph footer2 = new Paragraph("RC de " + ville + ": " + rc + " - IF: " + ifFiscal + " - TP: " + tp, footerFont);
            footer2.setAlignment(Element.ALIGN_CENTER);
            
            footerCell.addElement(footer1);
            footerCell.addElement(footer2);
            footerTable.addCell(footerCell);
            
            footerTable.writeSelectedRows(0, -1, xPosition, yPosition, canvas);
        }
        
        private void addLogoToPage(PdfWriter writer, Document document) {
            try {
                float logoWidth = 100f;
                float logoHeight = 75f;
                float xPosition = document.leftMargin();
                float yPosition = document.getPageSize().getHeight() - 2f;
                
                PdfPTable logoTable = new PdfPTable(1);
                logoTable.setTotalWidth(logoWidth);
                logoTable.setLockedWidth(true);
                
                PdfPCell logoCell = createLogoCell(writer, logoWidth, logoHeight);
                logoTable.addCell(logoCell);
                
                PdfContentByte canvas = writer.getDirectContent();
                logoTable.writeSelectedRows(0, -1, xPosition, yPosition, canvas);
            } catch (Exception e) {
                log.error("Error adding logo to Ordre Virement page", e);
            }
        }
    }

    // ========== EXPORTS PDF COMPTABLES ==========

    /**
     * Génère un PDF du journal comptable
     */
    public byte[] generateJournalComptable(List<com.bf4invest.model.EcritureComptable> ecritures, 
                                           java.time.LocalDate dateDebut, 
                                           java.time.LocalDate dateFin) throws DocumentException, IOException {
        Document document = new Document(PageSize.A4.rotate(), 40f, 40f, 60f, 60f);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();

        // Titre
        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, BLUE_DARK);
        Paragraph title = new Paragraph("JOURNAL COMPTABLE", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10f);
        document.add(title);

        // Période
        Font periodFont = new Font(Font.HELVETICA, 12, Font.NORMAL);
        Paragraph period = new Paragraph(
            String.format("Période: %s au %s", 
                dateDebut.format(DATE_FORMATTER), 
                dateFin.format(DATE_FORMATTER)), 
            periodFont);
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(20f);
        document.add(period);

        // Tableau
        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.5f, 1f, 1.5f, 2f, 3f, 1.5f, 1.5f});

        // En-têtes
        addTableHeader(table, "Date", BLUE_HEADER);
        addTableHeader(table, "Journal", BLUE_HEADER);
        addTableHeader(table, "Pièce", BLUE_HEADER);
        addTableHeader(table, "Compte", BLUE_HEADER);
        addTableHeader(table, "Libellé", BLUE_HEADER);
        addTableHeader(table, "Débit", BLUE_HEADER);
        addTableHeader(table, "Crédit", BLUE_HEADER);

        // Lignes
        for (com.bf4invest.model.EcritureComptable ecriture : ecritures) {
            if (ecriture.getLignes() != null) {
                for (com.bf4invest.model.LigneEcriture ligne : ecriture.getLignes()) {
                    table.addCell(createCell(ecriture.getDateEcriture().format(DATE_FORMATTER), Font.NORMAL, 10f));
                    table.addCell(createCell(ecriture.getJournal(), Font.NORMAL, 10f));
                    table.addCell(createCell(ecriture.getNumeroPiece(), Font.NORMAL, 10f));
                    table.addCell(createCell(ligne.getCompteCode(), Font.NORMAL, 10f));
                    table.addCell(createCell(ligne.getLibelle(), Font.NORMAL, 10f));
                    table.addCell(createCell(
                        ligne.getDebit() != null && ligne.getDebit() > 0 ? formatAmount(ligne.getDebit()) : "",
                        Font.NORMAL, 10f, Element.ALIGN_RIGHT));
                    table.addCell(createCell(
                        ligne.getCredit() != null && ligne.getCredit() > 0 ? formatAmount(ligne.getCredit()) : "",
                        Font.NORMAL, 10f, Element.ALIGN_RIGHT));
                }
            }
        }

        document.add(table);
        document.close();
        return baos.toByteArray();
    }

    /**
     * Génère un PDF de la balance
     */
    public byte[] generateBalance(List<com.bf4invest.model.CompteComptable> comptes,
                                 java.time.LocalDate dateDebut,
                                 java.time.LocalDate dateFin) throws DocumentException, IOException {
        Document document = new Document(PageSize.A4.rotate(), 40f, 40f, 60f, 60f);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();

        // Titre
        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, BLUE_DARK);
        Paragraph title = new Paragraph("BALANCE GÉNÉRALE", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10f);
        document.add(title);

        // Période
        Font periodFont = new Font(Font.HELVETICA, 12, Font.NORMAL);
        Paragraph period = new Paragraph(
            String.format("Période: %s au %s", 
                dateDebut.format(DATE_FORMATTER), 
                dateFin.format(DATE_FORMATTER)), 
            periodFont);
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(20f);
        document.add(period);

        // Tableau
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.5f, 4f, 1f, 2f, 2f, 2f});

        // En-têtes
        addTableHeader(table, "Code", BLUE_HEADER);
        addTableHeader(table, "Libellé", BLUE_HEADER);
        addTableHeader(table, "Classe", BLUE_HEADER);
        addTableHeader(table, "Débit", BLUE_HEADER);
        addTableHeader(table, "Crédit", BLUE_HEADER);
        addTableHeader(table, "Solde", BLUE_HEADER);

        // Lignes
        for (com.bf4invest.model.CompteComptable compte : comptes) {
            table.addCell(createCell(compte.getCode(), Font.NORMAL, 10f));
            table.addCell(createCell(compte.getLibelle(), Font.NORMAL, 10f));
            table.addCell(createCell(compte.getClasse(), Font.NORMAL, 10f));
            table.addCell(createCell(formatAmount(compte.getSoldeDebit()), Font.NORMAL, 10f, Element.ALIGN_RIGHT));
            table.addCell(createCell(formatAmount(compte.getSoldeCredit()), Font.NORMAL, 10f, Element.ALIGN_RIGHT));
            table.addCell(createCell(formatAmount(compte.getSolde()), Font.BOLD, 10f, Element.ALIGN_RIGHT));
        }

        document.add(table);
        document.close();
        return baos.toByteArray();
    }

    /**
     * Génère un PDF du grand livre
     */
    public byte[] generateGrandLivre(List<com.bf4invest.model.EcritureComptable> ecritures,
                                    String compteCode,
                                    String compteLibelle,
                                    java.time.LocalDate dateDebut,
                                    java.time.LocalDate dateFin) throws DocumentException, IOException {
        Document document = new Document(PageSize.A4.rotate(), 40f, 40f, 60f, 60f);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();

        // Titre
        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, BLUE_DARK);
        Paragraph title = new Paragraph("GRAND LIVRE", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(5f);
        document.add(title);

        // Compte
        Font compteFont = new Font(Font.HELVETICA, 14, Font.BOLD);
        Paragraph compte = new Paragraph(
            String.format("%s - %s", compteCode, compteLibelle), 
            compteFont);
        compte.setAlignment(Element.ALIGN_CENTER);
        compte.setSpacingAfter(10f);
        document.add(compte);

        // Période
        Font periodFont = new Font(Font.HELVETICA, 12, Font.NORMAL);
        Paragraph period = new Paragraph(
            String.format("Période: %s au %s", 
                dateDebut.format(DATE_FORMATTER), 
                dateFin.format(DATE_FORMATTER)), 
            periodFont);
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(20f);
        document.add(period);

        // Tableau
        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.5f, 1f, 1.5f, 3f, 1.5f, 1.5f, 2f});

        // En-têtes
        addTableHeader(table, "Date", BLUE_HEADER);
        addTableHeader(table, "Journal", BLUE_HEADER);
        addTableHeader(table, "Pièce", BLUE_HEADER);
        addTableHeader(table, "Libellé", BLUE_HEADER);
        addTableHeader(table, "Débit", BLUE_HEADER);
        addTableHeader(table, "Crédit", BLUE_HEADER);
        addTableHeader(table, "Solde Progressif", BLUE_HEADER);

        // Lignes avec solde progressif
        double soldeProgressif = 0.0;
        for (com.bf4invest.model.EcritureComptable ecriture : ecritures) {
            if (ecriture.getLignes() != null) {
                for (com.bf4invest.model.LigneEcriture ligne : ecriture.getLignes()) {
                    if (compteCode.equals(ligne.getCompteCode())) {
                        double debit = ligne.getDebit() != null ? ligne.getDebit() : 0.0;
                        double credit = ligne.getCredit() != null ? ligne.getCredit() : 0.0;
                        soldeProgressif += (debit - credit);

                        table.addCell(createCell(ecriture.getDateEcriture().format(DATE_FORMATTER), Font.NORMAL, 10f));
                        table.addCell(createCell(ecriture.getJournal(), Font.NORMAL, 10f));
                        table.addCell(createCell(ecriture.getNumeroPiece(), Font.NORMAL, 10f));
                        table.addCell(createCell(ligne.getLibelle(), Font.NORMAL, 10f));
                        table.addCell(createCell(debit > 0 ? formatAmount(debit) : "", Font.NORMAL, 10f, Element.ALIGN_RIGHT));
                        table.addCell(createCell(credit > 0 ? formatAmount(credit) : "", Font.NORMAL, 10f, Element.ALIGN_RIGHT));
                        table.addCell(createCell(formatAmount(soldeProgressif), Font.BOLD, 10f, Element.ALIGN_RIGHT));
                    }
                }
            }
        }

        document.add(table);
        document.close();
        return baos.toByteArray();
    }

    /**
     * Génère un PDF de déclaration TVA
     */
    public byte[] generateDeclarationTVAPDF(com.bf4invest.model.DeclarationTVA declaration) throws DocumentException, IOException {
        Document document = new Document(PageSize.A4, 40f, 40f, 60f, 60f);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();

        // Titre
        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, BLUE_DARK);
        Paragraph title = new Paragraph("DÉCLARATION TVA", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10f);
        document.add(title);

        // Période
        Font periodFont = new Font(Font.HELVETICA, 14, Font.BOLD);
        Paragraph period = new Paragraph("Période: " + declaration.getPeriode(), periodFont);
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(20f);
        document.add(period);

        // TVA Collectée
        Paragraph collecteeTitle = new Paragraph("TVA COLLECTÉE", new Font(Font.HELVETICA, 12, Font.BOLD));
        collecteeTitle.setSpacingAfter(10f);
        document.add(collecteeTitle);

        PdfPTable collecteeTable = new PdfPTable(2);
        collecteeTable.setWidthPercentage(50);
        collecteeTable.setWidths(new float[]{2f, 1.5f});
        addTableRow(collecteeTable, "TVA 20%", formatAmount(declaration.getTvaCollectee20() != null ? declaration.getTvaCollectee20() : 0.0));
        addTableRow(collecteeTable, "TVA 14%", formatAmount(declaration.getTvaCollectee14() != null ? declaration.getTvaCollectee14() : 0.0));
        addTableRow(collecteeTable, "TVA 10%", formatAmount(declaration.getTvaCollectee10() != null ? declaration.getTvaCollectee10() : 0.0));
        addTableRow(collecteeTable, "TVA 7%", formatAmount(declaration.getTvaCollectee7() != null ? declaration.getTvaCollectee7() : 0.0));
        addTableRow(collecteeTable, "TVA 0%", formatAmount(declaration.getTvaCollectee0() != null ? declaration.getTvaCollectee0() : 0.0));
        addTableRow(collecteeTable, "TOTAL", formatAmount(declaration.getTvaCollecteeTotale() != null ? declaration.getTvaCollecteeTotale() : 0.0), Font.BOLD);
        document.add(collecteeTable);

        document.add(new Paragraph(" ")); // Espace

        // TVA Déductible
        Paragraph deductibleTitle = new Paragraph("TVA DÉDUCTIBLE", new Font(Font.HELVETICA, 12, Font.BOLD));
        deductibleTitle.setSpacingAfter(10f);
        document.add(deductibleTitle);

        PdfPTable deductibleTable = new PdfPTable(2);
        deductibleTable.setWidthPercentage(50);
        deductibleTable.setWidths(new float[]{2f, 1.5f});
        addTableRow(deductibleTable, "TVA 20%", formatAmount(declaration.getTvaDeductible20() != null ? declaration.getTvaDeductible20() : 0.0));
        addTableRow(deductibleTable, "TVA 14%", formatAmount(declaration.getTvaDeductible14() != null ? declaration.getTvaDeductible14() : 0.0));
        addTableRow(deductibleTable, "TVA 10%", formatAmount(declaration.getTvaDeductible10() != null ? declaration.getTvaDeductible10() : 0.0));
        addTableRow(deductibleTable, "TVA 7%", formatAmount(declaration.getTvaDeductible7() != null ? declaration.getTvaDeductible7() : 0.0));
        addTableRow(deductibleTable, "TVA 0%", formatAmount(declaration.getTvaDeductible0() != null ? declaration.getTvaDeductible0() : 0.0));
        addTableRow(deductibleTable, "TOTAL", formatAmount(declaration.getTvaDeductibleTotale() != null ? declaration.getTvaDeductibleTotale() : 0.0), Font.BOLD);
        document.add(deductibleTable);

        document.add(new Paragraph(" ")); // Espace

        // Résultat
        Paragraph resultTitle = new Paragraph("RÉSULTAT", new Font(Font.HELVETICA, 14, Font.BOLD));
        resultTitle.setSpacingAfter(10f);
        document.add(resultTitle);

        PdfPTable resultTable = new PdfPTable(2);
        resultTable.setWidthPercentage(50);
        resultTable.setWidths(new float[]{2f, 1.5f});
        addTableRow(resultTable, "TVA à Payer", formatAmount(declaration.getTvaAPayer() != null ? declaration.getTvaAPayer() : 0.0), Font.BOLD);
        if (declaration.getTvaCredit() != null && declaration.getTvaCredit() > 0) {
            addTableRow(resultTable, "TVA Crédit", formatAmount(declaration.getTvaCredit()), Font.BOLD);
        }
        document.add(resultTable);

        document.close();
        return baos.toByteArray();
    }

    // Helpers pour les tableaux PDF
    private void addTableHeader(PdfPTable table, String text, Color bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text, new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE)));
        cell.setBackgroundColor(bgColor);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(8f);
        table.addCell(cell);
    }

    private PdfPCell createCell(String text, int fontStyle, float fontSize) {
        return createCell(text, fontStyle, fontSize, Element.ALIGN_LEFT);
    }

    private PdfPCell createCell(String text, int fontStyle, float fontSize, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, new Font(Font.HELVETICA, fontSize, fontStyle)));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5f);
        return cell;
    }

    private void addTableRow(PdfPTable table, String label, String value) {
        addTableRow(table, label, value, Font.NORMAL);
    }

    private void addTableRow(PdfPTable table, String label, String value, int fontStyle) {
        table.addCell(createCell(label, Font.NORMAL, 10f));
        table.addCell(createCell(value, fontStyle, 10f, Element.ALIGN_RIGHT));
    }

    /**
     * Génère un PDF du bilan
     */
    public byte[] generateBilanPdf(java.util.Map<String, Object> bilan) throws DocumentException, IOException {
        Document document = new Document(PageSize.A4, 40f, 40f, 60f, 60f);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();

        // Titre
        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, BLUE_DARK);
        Paragraph title = new Paragraph("BILAN", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10f);
        document.add(title);

        // Date
        java.time.LocalDate date = (java.time.LocalDate) bilan.get("date");
        Font dateFont = new Font(Font.HELVETICA, 12, Font.NORMAL);
        Paragraph datePara = new Paragraph("Date: " + date.format(DATE_FORMATTER), dateFont);
        datePara.setAlignment(Element.ALIGN_CENTER);
        datePara.setSpacingAfter(20f);
        document.add(datePara);

        // Tableau Bilan (2 colonnes: Actif et Passif)
        PdfPTable bilanTable = new PdfPTable(2);
        bilanTable.setWidthPercentage(100);
        bilanTable.setWidths(new float[]{1f, 1f});

        // En-têtes
        PdfPCell actifHeader = new PdfPCell(new Phrase("ACTIF", new Font(Font.HELVETICA, 12, Font.BOLD, Color.WHITE)));
        actifHeader.setBackgroundColor(BLUE_HEADER);
        actifHeader.setHorizontalAlignment(Element.ALIGN_CENTER);
        actifHeader.setPadding(10f);
        bilanTable.addCell(actifHeader);

        PdfPCell passifHeader = new PdfPCell(new Phrase("PASSIF", new Font(Font.HELVETICA, 12, Font.BOLD, Color.WHITE)));
        passifHeader.setBackgroundColor(BLUE_HEADER);
        passifHeader.setHorizontalAlignment(Element.ALIGN_CENTER);
        passifHeader.setPadding(10f);
        bilanTable.addCell(passifHeader);

        // Actif
        addBilanRow(bilanTable, "Actif Immobilisé", (Double) bilan.get("actifImmobilise"));
        addBilanRow(bilanTable, "Actif Circulant", (Double) bilan.get("actifCirculant"));
        addBilanRow(bilanTable, "Créances", (Double) bilan.get("creances"));
        addBilanRow(bilanTable, "TOTAL ACTIF", (Double) bilan.get("totalActif"), Font.BOLD);

        // Passif
        addBilanRow(bilanTable, "Capitaux Propres", (Double) bilan.get("capitauxPropres"));
        addBilanRow(bilanTable, "Dettes", (Double) bilan.get("dettes"));
        addBilanRow(bilanTable, "TOTAL PASSIF", (Double) bilan.get("totalPassif"), Font.BOLD);

        document.add(bilanTable);

        // Résultat
        Double resultat = (Double) bilan.get("resultat");
        if (resultat != null) {
            document.add(new Paragraph(" "));
            Paragraph resultPara = new Paragraph(
                "Résultat: " + formatAmount(resultat),
                new Font(Font.HELVETICA, 12, Font.BOLD));
            resultPara.setAlignment(Element.ALIGN_CENTER);
            document.add(resultPara);
        }

        document.close();
        return baos.toByteArray();
    }

    /**
     * Génère un PDF du CPC (Compte de Produits et Charges)
     */
    public byte[] generateCpcPdf(java.util.Map<String, Object> cpc) throws DocumentException, IOException {
        Document document = new Document(PageSize.A4, 40f, 40f, 60f, 60f);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();

        // Titre
        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, BLUE_DARK);
        Paragraph title = new Paragraph("COMPTE DE PRODUITS ET CHARGES (CPC)", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10f);
        document.add(title);

        // Période
        java.time.LocalDate dateDebut = (java.time.LocalDate) cpc.get("dateDebut");
        java.time.LocalDate dateFin = (java.time.LocalDate) cpc.get("dateFin");
        Font periodFont = new Font(Font.HELVETICA, 12, Font.NORMAL);
        Paragraph period = new Paragraph(
            String.format("Période: %s au %s", 
                dateDebut.format(DATE_FORMATTER), 
                dateFin.format(DATE_FORMATTER)), 
            periodFont);
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(20f);
        document.add(period);

        // Tableau CPC
        PdfPTable cpcTable = new PdfPTable(2);
        cpcTable.setWidthPercentage(80);
        cpcTable.setWidths(new float[]{2.5f, 1.5f});
        cpcTable.setHorizontalAlignment(Element.ALIGN_CENTER);

        // En-tête
        PdfPCell header1 = new PdfPCell(new Phrase("Poste", new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE)));
        header1.setBackgroundColor(BLUE_HEADER);
        header1.setHorizontalAlignment(Element.ALIGN_CENTER);
        header1.setPadding(8f);
        cpcTable.addCell(header1);

        PdfPCell header2 = new PdfPCell(new Phrase("Montant", new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE)));
        header2.setBackgroundColor(BLUE_HEADER);
        header2.setHorizontalAlignment(Element.ALIGN_CENTER);
        header2.setPadding(8f);
        cpcTable.addCell(header2);

        // Produits d'exploitation
        addCpcRow(cpcTable, "PRODUITS D'EXPLOITATION", (Double) cpc.get("produitsExploitation"), Font.BOLD);
        addCpcRow(cpcTable, "Charges d'exploitation", (Double) cpc.get("chargesExploitation"));
        addCpcRow(cpcTable, "Résultat d'exploitation", (Double) cpc.get("resultatExploitation"), Font.BOLD);

        cpcTable.addCell(createCell(" ", Font.NORMAL, 10f));
        cpcTable.addCell(createCell(" ", Font.NORMAL, 10f));

        // Produits et charges financiers
        addCpcRow(cpcTable, "PRODUITS FINANCIERS", (Double) cpc.get("produitsFinanciers"));
        addCpcRow(cpcTable, "Charges financières", (Double) cpc.get("chargesFinancieres"));
        addCpcRow(cpcTable, "Résultat financier", (Double) cpc.get("resultatFinancier"), Font.BOLD);

        cpcTable.addCell(createCell(" ", Font.NORMAL, 10f));
        cpcTable.addCell(createCell(" ", Font.NORMAL, 10f));

        addCpcRow(cpcTable, "Résultat courant", (Double) cpc.get("resultatCourant"), Font.BOLD);
        addCpcRow(cpcTable, "Impôts sur les bénéfices", (Double) cpc.get("impotBenefices"));
        addCpcRow(cpcTable, "RÉSULTAT NET", (Double) cpc.get("resultatNet"), Font.BOLD);

        document.add(cpcTable);
        document.close();
        return baos.toByteArray();
    }

    private void addBilanRow(PdfPTable table, String label, Double value) {
        addBilanRow(table, label, value, Font.NORMAL);
    }

    private void addBilanRow(PdfPTable table, String label, Double value, int fontStyle) {
        PdfPCell labelCell = createCell(label, fontStyle, 10f);
        labelCell.setPadding(8f);
        table.addCell(labelCell);

        PdfPCell valueCell = createCell(formatAmount(value != null ? value : 0.0), fontStyle, 10f, Element.ALIGN_RIGHT);
        valueCell.setPadding(8f);
        table.addCell(valueCell);
    }

    private void addCpcRow(PdfPTable table, String label, Double value) {
        addCpcRow(table, label, value, Font.NORMAL);
    }

    private void addCpcRow(PdfPTable table, String label, Double value, int fontStyle) {
        table.addCell(createCell(label, fontStyle, 10f));
        table.addCell(createCell(formatAmount(value != null ? value : 0.0), fontStyle, 10f, Element.ALIGN_RIGHT));
    }
}

