package com.bf4invest.pdf;

import com.bf4invest.model.*;
import com.bf4invest.repository.ClientRepository;
import com.bf4invest.repository.SupplierRepository;
import com.bf4invest.repository.BandeCommandeRepository;
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
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfService {
    
    private final ClientRepository clientRepository;
    private final SupplierRepository supplierRepository;
    private final BandeCommandeRepository bandeCommandeRepository;
    
    // Couleurs utilisées - ajustées pour correspondre aux images de référence
    private static final Color BLUE_DARK = new Color(30, 64, 124); // Bleu foncé pour logo
    private static final Color BLUE_LIGHT = new Color(200, 220, 240); // Bleu clair grisé pour les sections (comme référence)
    private static final Color BLUE_HEADER = new Color(70, 130, 180); // Bleu moyen pour les headers de tableau
    private static final Color RED = new Color(180, 0, 0); // Rouge foncé pour les éléments importants (comme référence)
    
    // Formatters
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    // Format français: espace insécable pour milliers, virgule pour décimales (ex: 1 515,83)
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
    // Format français: espace insécable pour milliers, virgule pour décimales (ex: 1 515,83)
    private static String formatAmount(Double amount) {
        if (amount == null) return "0,00";
        // Utiliser le format français (espace insécable pour milliers, virgule pour décimales)
        return FRENCH_NUMBER_FORMAT.format(amount);
    }
    
    public byte[] generateBC(BandeCommande bc) throws DocumentException, IOException {
        Document document = new Document(PageSize.A4, 40f, 40f, 60f, 60f);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        
        document.open();
        
        // Récupérer les informations client et fournisseur
        Client client = bc.getClientId() != null ? 
            clientRepository.findById(bc.getClientId()).orElse(null) : null;
        Supplier supplier = bc.getFournisseurId() != null ? 
            supplierRepository.findById(bc.getFournisseurId()).orElse(null) : null;
        
        // En-tête avec logo et numéro
        addBCHeader(document, bc, writer);
        
        // Section Destinataire
        addBCDestinataire(document, supplier);
        
        // Tableau des lignes
        addBCProductTable(document, bc);
        
        // Totaux
        addBCTotals(document, bc);
        
        // Informations livraison et paiement
        addBCDeliveryInfo(document, bc, client);
        
        // Footer
        addBCFooter(document);
        
        document.close();
        return baos.toByteArray();
    }
    
    public byte[] generateFactureVente(FactureVente facture) throws DocumentException, IOException {
        Document document = new Document(PageSize.A4, 40f, 40f, 60f, 60f);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        
        document.open();
        
        // Récupérer les informations client
        Client client = facture.getClientId() != null ? 
            clientRepository.findById(facture.getClientId()).orElse(null) : null;
        
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
        
        // Footer
        addFactureFooter(document);
        
        document.close();
        return baos.toByteArray();
    }
    
    public byte[] generateFactureAchat(FactureAchat facture) throws DocumentException, IOException {
        Document document = new Document(PageSize.A4, 40f, 40f, 60f, 60f);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        
        document.open();
        
        Supplier supplier = facture.getFournisseurId() != null ? 
            supplierRepository.findById(facture.getFournisseurId()).orElse(null) : null;
        
        addFactureAchatHeader(document, facture, supplier, writer);
        addFactureAchatInfo(document, facture);
        addFactureAchatProductTable(document, facture);
        addFactureAchatTotals(document, facture);
        addFactureFooter(document);
        
        document.close();
        return baos.toByteArray();
    }
    
    // ============ LOGO METHODS ============
    
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
        
        // Essayer de charger l'image depuis les ressources - plusieurs méthodes
        String[] extensions = {"png", "jpg", "jpeg", "gif"};
        for (String ext : extensions) {
            try {
                ClassPathResource resource = new ClassPathResource("images/logo." + ext);
                log.info("🔍 Recherche logo: images/logo.{} - existe={}, URI={}", 
                    ext, resource.exists(), resource.exists() ? resource.getURI().toString() : "N/A");
                
                if (resource.exists() && resource.isReadable()) {
                    try (InputStream is = resource.getInputStream()) {
                        byte[] imageBytes = is.readAllBytes();
                        log.info("📦 Logo {}: {} bytes lus", ext, imageBytes.length);
                        
                        if (imageBytes != null && imageBytes.length > 0) {
                            // Essayer plusieurs méthodes de chargement
                            // Méthode 1: byte[] direct
                            try {
                                logoImage = Image.getInstance(imageBytes);
                                log.info("✅ Logo chargé avec succès via byte[] ({} bytes)", imageBytes.length);
                            } catch (Exception e1) {
                                log.warn("❌ Échec byte[]: {}", e1.getMessage());
                                
                                // Méthode 2: URL
                                try {
                                    java.net.URL url = resource.getURL();
                                    logoImage = Image.getInstance(url);
                                    log.info("✅ Logo chargé avec succès via URL");
                                } catch (Exception e2) {
                                    log.warn("❌ Échec URL: {}", e2.getMessage());
                                    
                                    // Méthode 3: Fichier temporaire
                                    try {
                                        java.io.File tempFile = java.io.File.createTempFile("bf4logo_", "." + ext);
                                        tempFile.deleteOnExit();
                                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                                            fos.write(imageBytes);
                                            fos.flush();
                                        }
                                        logoImage = Image.getInstance(tempFile.getAbsolutePath());
                                        log.info("✅ Logo chargé via fichier temporaire: {}", tempFile.getAbsolutePath());
                                    } catch (Exception e3) {
                                        log.error("❌ Toutes les méthodes ont échoué: {}", e3.getMessage(), e3);
                                    }
                                }
                            }
                            
                            if (logoImage != null) {
                                // Redimensionner en gardant les proportions
                                float imgW = logoImage.getWidth();
                                float imgH = logoImage.getHeight();
                                float scale = Math.min((width - 4) / imgW, (height - 4) / imgH);
                                logoImage.scaleAbsolute(imgW * scale, imgH * scale);
                                log.info("📏 Logo redimensionné: {}x{} -> {}x{}", 
                                    imgW, imgH, logoImage.getScaledWidth(), logoImage.getScaledHeight());
                                break;
                            }
                        }
                    } catch (Exception e) {
                        log.error("❌ Erreur lecture logo.{}: {}", ext, e.getMessage(), e);
                    }
                }
            } catch (Exception e) {
                log.warn("Erreur lors du chargement de logo.{}: {}", ext, e.getMessage());
                // Continuer avec l'extension suivante
            }
        }
        
        // Si aucune image trouvée, créer un logo dessiné programmatiquement
        if (logoImage == null) {
            log.warn("Aucune image logo trouvée dans les ressources, utilisation du logo dessiné programmatiquement");
            try {
                logoImage = createDrawnLogo(writer, width, height);
            } catch (Exception e) {
                log.error("Erreur lors de la création du logo dessiné: {}", e.getMessage());
            }
        }
        
        if (logoImage != null) {
            // Ajouter l'image directement à la cellule (elle sera centrée automatiquement)
            logoCell.addElement(logoImage);
            log.info("✅ Logo ajouté à la cellule PDF ({}x{})", 
                logoImage.getScaledWidth(), logoImage.getScaledHeight());
        } else {
            log.error("❌ Échec création logo - utilisation du fallback dessiné");
            try {
                logoImage = createDrawnLogo(writer, width, height);
                logoCell.addElement(logoImage);
            } catch (Exception e) {
                log.error("Erreur logo fallback: {}", e.getMessage());
                // Dernier recours: texte
                Paragraph fallback = new Paragraph("BF4\nINVEST", 
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(33, 150, 243)));
                fallback.setAlignment(Element.ALIGN_CENTER);
                logoCell.addElement(fallback);
            }
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
        // Table pour le header (logo à gauche, titre et numéro au centre/droite)
        PdfPTable headerTable = new PdfPTable(3);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{2f, 3f, 3f});
        headerTable.setSpacingAfter(15);
        
        // Logo stylisé avec lignes courbes
        PdfPCell logoCell = createLogoCell(writer, 80f, 60f);
        headerTable.addCell(logoCell);
        
        // Titre "BON DE COMMANDEN°" avec numéro sur la même ligne
        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        Paragraph title = new Paragraph();
        // Texte "BON DE COMMANDEN°" en noir
        Chunk titleChunk = new Chunk("BON DE COMMANDEN°", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));
        title.add(titleChunk);
        // Numéro en rouge directement après
        String numeroBC = bc.getNumeroBC() != null ? bc.getNumeroBC() : "";
        Chunk numeroChunk = new Chunk(" " + numeroBC, 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, RED));
        title.add(numeroChunk);
        title.setAlignment(Element.ALIGN_LEFT);
        titleCell.addElement(title);
        headerTable.addCell(titleCell);
        
        // Date à droite
        PdfPCell dateCell = new PdfPCell();
        dateCell.setBorder(Rectangle.NO_BORDER);
        dateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph dateLabel = new Paragraph("MEKNES LE:", 
            FontFactory.getFont(FontFactory.HELVETICA, 10));
        dateLabel.setAlignment(Element.ALIGN_RIGHT);
        dateCell.addElement(dateLabel);
        
        String dateStr = bc.getDateBC() != null ? bc.getDateBC().format(DATE_FORMATTER) : "";
        Paragraph dateValue = new Paragraph(dateStr, 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, RED));
        dateValue.setAlignment(Element.ALIGN_RIGHT);
        dateCell.addElement(dateValue);
        headerTable.addCell(dateCell);
        
        document.add(headerTable);
    }
    
    private void addBCDestinataire(Document document, Supplier supplier) throws DocumentException {
        PdfPTable destTable = new PdfPTable(2);
        destTable.setWidthPercentage(100);
        destTable.setSpacingAfter(15);
        destTable.setWidths(new float[]{2f, 5f});
        
        // Cellule gauche avec fond bleu clair
        PdfPCell labelCell = new PdfPCell(new Phrase("DESTINATAIRE :", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        labelCell.setBackgroundColor(BLUE_LIGHT);
        labelCell.setPadding(8);
        labelCell.setBorder(Rectangle.BOX);
        labelCell.setBorderColor(Color.WHITE);
        destTable.addCell(labelCell);
        
        // Cellule droite avec nom du fournisseur en rouge et souligné
        PdfPCell nameCell = new PdfPCell();
        nameCell.setBorder(Rectangle.BOX);
        nameCell.setBorderColor(Color.WHITE);
        nameCell.setPadding(8);
        String supplierName = supplier != null ? supplier.getNom() : "";
        Font nameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.UNDERLINE, RED);
        Paragraph name = new Paragraph(supplierName, nameFont);
        nameCell.addElement(name);
        destTable.addCell(nameCell);
        
        document.add(destTable);
    }
    
    private void addBCProductTable(Document document, BandeCommande bc) throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{0.5f, 3f, 1f, 1.5f, 2f, 2.5f});
        table.setSpacingAfter(15);
        
        // En-têtes avec fond bleu clair et texte bleu foncé
        String[] headers = {"N°", "Désignation", "Unité", "Quantité", "PU HT", "PrixTotalHT"};
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BLUE_HEADER);
        
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(BLUE_LIGHT);
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBorderColor(Color.WHITE);
            table.addCell(cell);
        }
        
        // Lignes de produits
        if (bc.getLignes() != null && !bc.getLignes().isEmpty()) {
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
                double totalHT = (ligne.getQuantiteAchetee() != null ? ligne.getQuantiteAchetee() : 0) * 
                    (ligne.getPrixAchatUnitaireHT() != null ? ligne.getPrixAchatUnitaireHT() : 0);
                addTableCell(table, formatAmount(totalHT), Element.ALIGN_RIGHT);
            }
        }
        
        document.add(table);
    }
    
    private void addBCTotals(Document document, BandeCommande bc) throws DocumentException {
        PdfPTable totalsTable = new PdfPTable(4);
        totalsTable.setWidthPercentage(60);
        totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalsTable.setWidths(new float[]{2f, 1.5f, 1.5f, 2f});
        totalsTable.setSpacingAfter(20);
        
        // Pour le BC, on utilise les quantités achetées pour calculer les totaux
        double totalHT = 0.0;
        double tauxTVA = 20.0;
        if (bc.getLignes() != null && !bc.getLignes().isEmpty()) {
            for (var ligne : bc.getLignes()) {
                double qty = ligne.getQuantiteAchetee() != null ? ligne.getQuantiteAchetee() : 0;
                double puHT = ligne.getPrixAchatUnitaireHT() != null ? ligne.getPrixAchatUnitaireHT() : 0;
                totalHT += qty * puHT;
            }
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
        
        // LIEU DE LIVRAISON
        addInfoRowWithBackground(infoTable, "LIEU DE LIVRAISON:", 
            client != null && client.getAdresse() != null ? client.getAdresse() : "", 
            labelFont, valueFontRed);
        
        // CONDITION DE LIVRAISON
        addInfoRowWithBackground(infoTable, "CONDITION DE LIVRAISON:", "LIVRAISON IMMEDIATE", 
            labelFont, valueFontRed);
        
        // RESPONSABLE A CONTACTER
        String responsable = client != null && client.getContacts() != null && !client.getContacts().isEmpty() ?
            client.getContacts().get(0).getNom() : "N/A";
        addInfoRowWithBackground(infoTable, "RESPONSABLE A CONTACTER A LA\nLIVRAISON", responsable, 
            labelFont, valueFontRed);
        
        // MODE PAIEMENT (récupérer depuis la BC ou utiliser une valeur par défaut)
        String modePaiement = bc.getModePaiement() != null && !bc.getModePaiement().isEmpty() 
            ? bc.getModePaiement() 
            : "120J"; // Valeur par défaut
        addInfoRowWithBackground(infoTable, "MODE PAIEMENT :", modePaiement, labelFont, valueFontRed);
        
        document.add(infoTable);
    }
    
    private void addBCFooter(Document document) throws DocumentException {
        PdfPTable footerTable = new PdfPTable(1);
        footerTable.setWidthPercentage(100);
        footerTable.setSpacingBefore(30);
        
        PdfPCell footerCell = new PdfPCell();
        footerCell.setBackgroundColor(BLUE_LIGHT);
        footerCell.setPadding(10);
        footerCell.setBorder(Rectangle.NO_BORDER);
        
        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
        
        Paragraph footer1 = new Paragraph("ICE: 002889872000062", footerFont);
        Paragraph footer2 = new Paragraph("BF4 INVEST SARL au capital de 2.000.000,00 Dhs, Tel: 06 61 51 11 91", footerFont);
        Paragraph footer3 = new Paragraph("RC de Meknes: 54287 - IF: 50499801 - TP: 17101980", footerFont);
        
        footerCell.addElement(footer1);
        footerCell.addElement(footer2);
        footerCell.addElement(footer3);
        
        footerTable.addCell(footerCell);
        document.add(footerTable);
    }
    
    // ============ FACTURE VENTE METHODS ============
    
    private void addFactureHeader(Document document, FactureVente facture, Client client, PdfWriter writer) throws DocumentException, IOException {
        PdfPTable headerTable = new PdfPTable(3);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{2f, 4f, 4f});
        headerTable.setSpacingAfter(15);
        
        // Logo stylisé avec lignes courbes
        PdfPCell logoCell = createLogoCell(writer, 80f, 60f);
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
        
        String ice = client != null && client.getIce() != null ? client.getIce() : "";
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
        
        PdfPCell tvaAmount = new PdfPCell(new Phrase(formatAmount(montantTVA), 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        tvaAmount.setBorder(Rectangle.NO_BORDER);
        tvaAmount.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tvaAmount.setPadding(5);
        totalsTable.addCell(tvaAmount);
        
        addTotalsRow(totalsTable, "TOTAL TTC", formatAmount(totalTTC), true);
        
        document.add(totalsTable);
    }
    
    private void addFactureAmountInWords(Document document, FactureVente facture) throws DocumentException {
        double totalTTC = facture.getTotalTTC() != null ? facture.getTotalTTC() : 0.0;
        String amountInWords = convertAmountToFrenchWords(totalTTC);
        
        Paragraph amountPara = new Paragraph(
            "Arrêter la présente facture à la somme TTC de :\n" + amountInWords,
            FontFactory.getFont(FontFactory.HELVETICA, 9));
        amountPara.setSpacingBefore(15);
        amountPara.setSpacingAfter(30);
        document.add(amountPara);
    }
    
    private void addFactureFooter(Document document) throws DocumentException {
        PdfPTable footerTable = new PdfPTable(1);
        footerTable.setWidthPercentage(100);
        
        PdfPCell footerCell = new PdfPCell();
        footerCell.setBackgroundColor(BLUE_LIGHT);
        footerCell.setPadding(10);
        footerCell.setBorder(Rectangle.NO_BORDER);
        
        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
        
        Paragraph footer1 = new Paragraph("ICE: 002889872000062", footerFont);
        Paragraph footer2 = new Paragraph("BF4 INVEST SARL au capital de 2.000.000,00 Dhs, Tél: 06 61 51 11 91", footerFont);
        Paragraph footer3 = new Paragraph("RC de Meknès: 54287 - IF: 50499801 - TP: 17101980", footerFont);
        
        footerCell.addElement(footer1);
        footerCell.addElement(footer2);
        footerCell.addElement(footer3);
        
        footerTable.addCell(footerCell);
        document.add(footerTable);
    }
    
    // ============ FACTURE ACHAT METHODS ============
    
    private void addFactureAchatHeader(Document document, FactureAchat facture, Supplier supplier, PdfWriter writer) throws DocumentException, IOException {
        PdfPTable headerTable = new PdfPTable(3);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{2f, 4f, 4f});
        headerTable.setSpacingAfter(15);
        
        // Logo stylisé avec lignes courbes
        PdfPCell logoCell = createLogoCell(writer, 80f, 60f);
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
            if (unitsPart == 1 && tensPart == 8) {
                result += "-Et-Un";
            } else if (unitsPart > 0) {
                result += (unitsPart == 1 ? "-Et-Un" : "-" + units[(int) unitsPart]);
            } else if (tensPart == 8) {
                result += "s";
            }
            return result;
        }
        
        if (number < 1000) {
            long hundreds = number / 100;
            long remainder = number % 100;
            
            String result = (hundreds == 1 ? "Cent" : units[(int) hundreds] + "-Cent");
            if (remainder == 0 && hundreds > 1) {
                result += "s";
            }
            if (remainder > 0) {
                result += "-" + convertNumberToFrench(remainder);
            }
            return result;
        }
        
        if (number < 1000000) {
            long thousands = number / 1000;
            long remainder = number % 1000;
            
            String result = convertNumberToFrench(thousands);
            if (thousands == 1) {
                result += "-Mille";
            } else {
                result += "-Mille";
            }
            if (remainder > 0) {
                result += "-" + convertNumberToFrench(remainder);
            }
            return result;
        }
        
        // Pour les millions (cas général simplifié)
        return String.valueOf(number);
    }
    
    // ============ DASHBOARD REPORT METHOD ============
    
    public byte[] generateDashboardReport(com.bf4invest.dto.DashboardKpiResponse kpis, 
                                          java.time.LocalDate from, 
                                          java.time.LocalDate to) throws DocumentException, IOException {
        Document document = new Document(PageSize.A4, 40f, 40f, 60f, 60f);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        
        document.open();
        
        // Page de garde
        addReportCoverPage(document, writer, from, to);
        document.newPage();
        
        // Synthèse exécutive
        addExecutiveSummary(document, kpis);
        
        // Analyse TVA
        addTvaAnalysis(document, kpis);
        
        // Situation des impayés
        addImpayesSection(document, kpis);
        
        // Évolution mensuelle du CA
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
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(15f);
        document.add(sectionTitle);
        
        PdfPTable summaryTable = new PdfPTable(2);
        summaryTable.setWidthPercentage(100);
        summaryTable.setWidths(new float[]{2f, 3f});
        
        addSummaryRow(summaryTable, "Chiffre d'Affaires HT:", formatCurrency(kpis.getCaHT()));
        addSummaryRow(summaryTable, "Chiffre d'Affaires TTC:", formatCurrency(kpis.getCaTTC()));
        addSummaryRow(summaryTable, "Total Achats HT:", formatCurrency(kpis.getTotalAchatsHT()));
        addSummaryRow(summaryTable, "Total Achats TTC:", formatCurrency(kpis.getTotalAchatsTTC()));
        addSummaryRow(summaryTable, "Marge Nette:", formatCurrency(kpis.getMargeTotale()));
        addSummaryRow(summaryTable, "Marge Moyenne:", String.format("%.2f%%", kpis.getMargeMoyenne()));
        
        document.add(summaryTable);
        document.add(new Paragraph(" ")); // Espacement
    }
    
    private void addTvaAnalysis(Document document, com.bf4invest.dto.DashboardKpiResponse kpis) 
            throws DocumentException {
        Paragraph sectionTitle = new Paragraph("2. ANALYSE TVA", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(15f);
        document.add(sectionTitle);
        
        PdfPTable tvaTable = new PdfPTable(2);
        tvaTable.setWidthPercentage(100);
        tvaTable.setWidths(new float[]{2f, 3f});
        
        addSummaryRow(tvaTable, "TVA Collectée:", formatCurrency(kpis.getTvaCollectee()));
        addSummaryRow(tvaTable, "TVA Déductible:", formatCurrency(kpis.getTvaDeductible()));
        
        double soldeTva = kpis.getTvaCollectee() - kpis.getTvaDeductible();
        PdfPCell labelCell = new PdfPCell(new Paragraph("Solde TVA:", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
        labelCell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        labelCell.setPadding(8f);
        tvaTable.addCell(labelCell);
        
        PdfPCell valueCell = new PdfPCell(new Paragraph(formatCurrency(soldeTva), 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
        valueCell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        valueCell.setPadding(8f);
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
        
        Paragraph sectionTitle = new Paragraph("3. SITUATION DES IMPAYÉS", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(15f);
        document.add(sectionTitle);
        
        PdfPTable impayesTable = new PdfPTable(3);
        impayesTable.setWidthPercentage(100);
        impayesTable.setWidths(new float[]{2f, 2f, 1f});
        
        // Header
        addTableHeaderCell(impayesTable, "Tranche", true);
        addTableHeaderCell(impayesTable, "Montant", true);
        addTableHeaderCell(impayesTable, "%", true);
        
        double totalImpayes = kpis.getImpayes().getTotalImpayes();
        double impayes0_30 = kpis.getImpayes().getImpayes0_30();
        double impayes31_60 = kpis.getImpayes().getImpayes31_60();
        double impayesPlus60 = kpis.getImpayes().getImpayesPlus60();
        
        // Rows
        addImpayesRow(impayesTable, "0-30 jours", impayes0_30, totalImpayes);
        addImpayesRow(impayesTable, "31-60 jours", impayes31_60, totalImpayes);
        addImpayesRow(impayesTable, "60+ jours", impayesPlus60, totalImpayes);
        
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
        
        Paragraph sectionTitle = new Paragraph("4. ÉVOLUTION MENSUELLE DU CA", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(15f);
        document.add(sectionTitle);
        
        PdfPTable monthlyTable = new PdfPTable(3);
        monthlyTable.setWidthPercentage(100);
        monthlyTable.setWidths(new float[]{2f, 2f, 1f});
        
        // Header
        addTableHeaderCell(monthlyTable, "Mois", true);
        addTableHeaderCell(monthlyTable, "CA HT", true);
        addTableHeaderCell(monthlyTable, "Marge %", true);
        
        // Limit to last 12 months
        List<com.bf4invest.dto.DashboardKpiResponse.MonthlyData> monthlyData = kpis.getCaMensuel();
        if (monthlyData.size() > 12) {
            monthlyData = monthlyData.subList(monthlyData.size() - 12, monthlyData.size());
        }
        
        for (com.bf4invest.dto.DashboardKpiResponse.MonthlyData month : monthlyData) {
            addTableCell(monthlyTable, formatMonth(month.getMois()), false);
            addTableCell(monthlyTable, formatCurrency(month.getCaHT()), false);
            addTableCell(monthlyTable, String.format("%.1f%%", month.getMarge()), false);
        }
        
        document.add(monthlyTable);
        document.add(new Paragraph(" "));
    }
    
    private void addTopClients(Document document, com.bf4invest.dto.DashboardKpiResponse kpis) 
            throws DocumentException {
        if (kpis.getTopClients() == null || kpis.getTopClients().isEmpty()) return;
        
        Paragraph sectionTitle = new Paragraph("5. TOP 10 CLIENTS", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(15f);
        document.add(sectionTitle);
        
        PdfPTable clientsTable = new PdfPTable(2);
        clientsTable.setWidthPercentage(100);
        clientsTable.setWidths(new float[]{3f, 2f});
        
        // Header
        addTableHeaderCell(clientsTable, "Client", true);
        addTableHeaderCell(clientsTable, "Montant", true);
        
        // Limit to top 10
        List<com.bf4invest.dto.DashboardKpiResponse.FournisseurClientStat> topClients = kpis.getTopClients();
        if (topClients.size() > 10) {
            topClients = topClients.subList(0, 10);
        }
        
        for (com.bf4invest.dto.DashboardKpiResponse.FournisseurClientStat client : topClients) {
            addTableCell(clientsTable, client.getNom(), false);
            addTableCell(clientsTable, formatCurrency(client.getMontant()), false);
        }
        
        document.add(clientsTable);
        document.add(new Paragraph(" "));
    }
    
    private void addTopSuppliers(Document document, com.bf4invest.dto.DashboardKpiResponse kpis) 
            throws DocumentException {
        if (kpis.getTopFournisseurs() == null || kpis.getTopFournisseurs().isEmpty()) return;
        
        Paragraph sectionTitle = new Paragraph("6. TOP 10 FOURNISSEURS", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(15f);
        document.add(sectionTitle);
        
        PdfPTable suppliersTable = new PdfPTable(2);
        suppliersTable.setWidthPercentage(100);
        suppliersTable.setWidths(new float[]{3f, 2f});
        
        // Header
        addTableHeaderCell(suppliersTable, "Fournisseur", true);
        addTableHeaderCell(suppliersTable, "Montant", true);
        
        // Limit to top 10
        List<com.bf4invest.dto.DashboardKpiResponse.FournisseurClientStat> topSuppliers = kpis.getTopFournisseurs();
        if (topSuppliers.size() > 10) {
            topSuppliers = topSuppliers.subList(0, 10);
        }
        
        for (com.bf4invest.dto.DashboardKpiResponse.FournisseurClientStat supplier : topSuppliers) {
            addTableCell(suppliersTable, supplier.getNom(), false);
            addTableCell(suppliersTable, formatCurrency(supplier.getMontant()), false);
        }
        
        document.add(suppliersTable);
        document.add(new Paragraph(" "));
    }
    
    private void addAlertsSection(Document document, com.bf4invest.dto.DashboardKpiResponse kpis) 
            throws DocumentException {
        Paragraph sectionTitle = new Paragraph("7. ALERTES ET ACTIONS REQUISES", 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK));
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(15f);
        document.add(sectionTitle);
        
        PdfPTable alertsTable = new PdfPTable(2);
        alertsTable.setWidthPercentage(100);
        alertsTable.setWidths(new float[]{2f, 3f});
        
        addSummaryRow(alertsTable, "Factures en retard:", String.valueOf(kpis.getFacturesEnRetard()));
        
        if (kpis.getImpayes() != null) {
            addSummaryRow(alertsTable, "Montant total impayé:", 
                formatCurrency(kpis.getImpayes().getTotalImpayes()));
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
    
    private void addTableHeaderCell(PdfPTable table, String text, boolean bold) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, 
            FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, 10)));
        cell.setBackgroundColor(BLUE_LIGHT);
        cell.setBorder(Rectangle.BOX);
        cell.setPadding(8f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }
    
    private void addTableCell(PdfPTable table, String text, boolean rightAlign) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, 
            FontFactory.getFont(FontFactory.HELVETICA, 10)));
        cell.setBorder(Rectangle.BOX);
        cell.setPadding(8f);
        cell.setHorizontalAlignment(rightAlign ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        table.addCell(cell);
    }
    
    private void addImpayesRow(PdfPTable table, String tranche, double montant, double total) {
        addTableCell(table, tranche, false);
        addTableCell(table, formatCurrency(montant), true);
        double percent = total > 0 ? (montant / total) * 100 : 0;
        addTableCell(table, String.format("%.1f%%", percent), true);
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
}
