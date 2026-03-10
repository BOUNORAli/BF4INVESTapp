package com.bf4invest.service;

import com.bf4invest.model.AcompteIS;
import com.bf4invest.model.DeclarationIS;
import com.bf4invest.model.ISBaremeConfig;
import com.bf4invest.repository.AcompteISRepository;
import com.bf4invest.repository.DeclarationISRepository;
import com.bf4invest.repository.ISBaremeConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ISService {

    private final ComptabiliteService comptabiliteService;
    private final DeclarationISRepository declarationISRepository;
    private final AcompteISRepository acompteISRepository;
    private final ISBaremeConfigRepository baremeConfigRepository;

    @Transactional
    public DeclarationIS calculerEtEnregistrerDeclaration(
            Integer annee,
            LocalDate dateDebut,
            LocalDate dateFin,
            String exerciceId,
            List<DeclarationIS.AjustementFiscal> reintegrations,
            List<DeclarationIS.AjustementFiscal> deductions
    ) {
        int safeAnnee = annee != null ? annee : (dateFin != null ? dateFin.getYear() : LocalDate.now().getYear());
        LocalDate debut = dateDebut != null ? dateDebut : LocalDate.of(safeAnnee, 1, 1);
        LocalDate fin = dateFin != null ? dateFin : LocalDate.of(safeAnnee, 12, 31);

        Map<String, Object> cpc = comptabiliteService.getCPC(debut, fin, exerciceId);
        double resultatComptable = asDouble(cpc.get("resultatNet"));
        double chiffreAffaires = asDouble(cpc.get("produitsExploitation"));
        double totalReintegrations = sumAjustements(reintegrations);
        double totalDeductions = sumAjustements(deductions);
        double resultatFiscal = resultatComptable + totalReintegrations - totalDeductions;

        ISBaremeConfig cfg = getOrCreateBareme();
        double isCalcule = calculISProgressif(resultatFiscal, cfg);
        double cotisationMinimale = Math.max(chiffreAffaires * nz(cfg.getCotisationMinimaleTaux()), nz(cfg.getCotisationMinimaleMinimum()));
        double isDu = Math.max(isCalcule, cotisationMinimale);

        List<AcompteIS> acomptes = getOrGenerateAcomptes(safeAnnee);
        double acomptesPayes = acomptes.stream()
                .filter(a -> a.getStatut() == AcompteIS.StatutAcompte.PAYE)
                .mapToDouble(a -> nz(a.getMontantPaye()))
                .sum();

        double reliquat = isDu > acomptesPayes ? (isDu - acomptesPayes) : 0.0;
        double excedent = acomptesPayes > isDu ? (acomptesPayes - isDu) : 0.0;

        DeclarationIS declaration = declarationISRepository.findByAnnee(safeAnnee)
                .orElseGet(DeclarationIS::new);
        declaration.setAnnee(safeAnnee);
        declaration.setDateDebut(debut);
        declaration.setDateFin(fin);
        declaration.setExerciceId(exerciceId);
        declaration.setResultatComptable(resultatComptable);
        declaration.setReintegrations(reintegrations != null ? reintegrations : List.of());
        declaration.setDeductions(deductions != null ? deductions : List.of());
        declaration.setTotalReintegrations(totalReintegrations);
        declaration.setTotalDeductions(totalDeductions);
        declaration.setResultatFiscal(resultatFiscal);
        declaration.setChiffreAffaires(chiffreAffaires);
        declaration.setIsCalcule(isCalcule);
        declaration.setCotisationMinimale(cotisationMinimale);
        declaration.setIsDu(isDu);
        declaration.setAcomptesPayes(acomptesPayes);
        declaration.setReliquat(reliquat);
        declaration.setExcedent(excedent);
        declaration.setStatut(DeclarationIS.StatutDeclaration.BROUILLON);
        declaration.setUpdatedAt(LocalDateTime.now());
        if (declaration.getCreatedAt() == null) {
            declaration.setCreatedAt(LocalDateTime.now());
        }
        return declarationISRepository.save(declaration);
    }

    public List<DeclarationIS> getDeclarations() {
        return declarationISRepository.findAllByOrderByAnneeDesc();
    }

    public Optional<DeclarationIS> getDeclarationByAnnee(Integer annee) {
        return declarationISRepository.findByAnnee(annee);
    }

    @Transactional
    public DeclarationIS validerDeclaration(Integer annee) {
        DeclarationIS declaration = declarationISRepository.findByAnnee(annee)
                .orElseThrow(() -> new IllegalArgumentException("Declaration IS introuvable pour " + annee));
        declaration.setStatut(DeclarationIS.StatutDeclaration.VALIDEE);
        declaration.setUpdatedAt(LocalDateTime.now());
        return declarationISRepository.save(declaration);
    }

    public List<AcompteIS> getOrGenerateAcomptes(Integer annee) {
        List<AcompteIS> existing = acompteISRepository.findByAnneeOrderByTrimestreAsc(annee);
        if (!existing.isEmpty()) {
            return refreshAcompteStatus(existing);
        }

        double isReference = declarationISRepository.findByAnnee(annee - 1)
                .map(DeclarationIS::getIsDu)
                .orElseGet(() -> {
                    Map<String, Object> previous = calculerIS(
                            LocalDate.of(annee - 1, 1, 1),
                            LocalDate.of(annee - 1, 12, 31),
                            null
                    );
                    return asDouble(previous.get("isAPayer"));
                });
        double montantAcompte = isReference * 0.25;

        LocalDateTime now = LocalDateTime.now();
        List<AcompteIS> generated = new ArrayList<>();
        generated.add(buildAcompte(annee, 1, LocalDate.of(annee, 3, 31), montantAcompte, now));
        generated.add(buildAcompte(annee, 2, LocalDate.of(annee, 6, 30), montantAcompte, now));
        generated.add(buildAcompte(annee, 3, LocalDate.of(annee, 9, 30), montantAcompte, now));
        generated.add(buildAcompte(annee, 4, LocalDate.of(annee, 12, 31), montantAcompte, now));
        return acompteISRepository.saveAll(generated);
    }

    @Transactional
    public AcompteIS marquerAcomptePaye(String acompteId, LocalDate datePaiement, Double montantPaye) {
        AcompteIS acompte = acompteISRepository.findById(acompteId)
                .orElseThrow(() -> new IllegalArgumentException("Acompte IS introuvable"));
        acompte.setDatePaiement(datePaiement != null ? datePaiement : LocalDate.now());
        acompte.setMontantPaye(montantPaye != null ? montantPaye : acompte.getMontant());
        acompte.setStatut(AcompteIS.StatutAcompte.PAYE);
        acompte.setUpdatedAt(LocalDateTime.now());
        return acompteISRepository.save(acompte);
    }

    @Transactional
    public ISBaremeConfig updateBareme(ISBaremeConfig payload) {
        ISBaremeConfig cfg = getOrCreateBareme();
        cfg.setSeuilTaux1(payload.getSeuilTaux1());
        cfg.setSeuilTaux2(payload.getSeuilTaux2());
        cfg.setTaux1(payload.getTaux1());
        cfg.setTaux2(payload.getTaux2());
        cfg.setTaux3(payload.getTaux3());
        cfg.setCotisationMinimaleTaux(payload.getCotisationMinimaleTaux());
        cfg.setCotisationMinimaleMinimum(payload.getCotisationMinimaleMinimum());
        cfg.setUpdatedAt(LocalDateTime.now());
        return baremeConfigRepository.save(cfg);
    }

    public ISBaremeConfig getBareme() {
        return getOrCreateBareme();
    }

    public byte[] exportDeclaration(Integer annee) throws IOException {
        DeclarationIS declaration = declarationISRepository.findByAnnee(annee)
                .orElseThrow(() -> new IllegalArgumentException("Aucune declaration IS pour l'annee " + annee));
        List<AcompteIS> acomptes = getOrGenerateAcomptes(annee);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet synthese = workbook.createSheet("Declaration IS");
            CellStyle headerStyle = workbook.createCellStyle();
            var font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            int r = 0;
            Row title = synthese.createRow(r++);
            title.createCell(0).setCellValue("Declaration IS - " + annee);
            title.getCell(0).setCellStyle(headerStyle);

            r++;
            r = addLine(synthese, r, "Resultat comptable", declaration.getResultatComptable());
            r = addLine(synthese, r, "Total reintegrations", declaration.getTotalReintegrations());
            r = addLine(synthese, r, "Total deductions", declaration.getTotalDeductions());
            r = addLine(synthese, r, "Resultat fiscal", declaration.getResultatFiscal());
            r = addLine(synthese, r, "IS calcule", declaration.getIsCalcule());
            r = addLine(synthese, r, "Cotisation minimale", declaration.getCotisationMinimale());
            r = addLine(synthese, r, "IS du", declaration.getIsDu());
            r = addLine(synthese, r, "Acomptes payes", declaration.getAcomptesPayes());
            r = addLine(synthese, r, "Reliquat", declaration.getReliquat());
            r = addLine(synthese, r, "Excedent", declaration.getExcedent());

            Sheet acomptesSheet = workbook.createSheet("Acomptes");
            Row h = acomptesSheet.createRow(0);
            h.createCell(0).setCellValue("Trimestre");
            h.createCell(1).setCellValue("Echeance");
            h.createCell(2).setCellValue("Montant");
            h.createCell(3).setCellValue("Statut");
            h.createCell(4).setCellValue("Date paiement");
            h.createCell(5).setCellValue("Montant paye");
            for (int i = 0; i <= 5; i++) {
                h.getCell(i).setCellStyle(headerStyle);
            }
            int ar = 1;
            for (AcompteIS a : acomptes) {
                Row row = acomptesSheet.createRow(ar++);
                row.createCell(0).setCellValue(a.getTrimestre());
                row.createCell(1).setCellValue(a.getDateEcheance() != null ? a.getDateEcheance().toString() : "");
                row.createCell(2).setCellValue(nz(a.getMontant()));
                row.createCell(3).setCellValue(a.getStatut() != null ? a.getStatut().name() : "");
                row.createCell(4).setCellValue(a.getDatePaiement() != null ? a.getDatePaiement().toString() : "");
                row.createCell(5).setCellValue(nz(a.getMontantPaye()));
            }

            for (int i = 0; i < 2; i++) {
                synthese.autoSizeColumn(i);
            }
            for (int i = 0; i <= 5; i++) {
                acomptesSheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Compatibilite ascendante endpoint historique.
     */
    public Map<String, Object> calculerIS(LocalDate dateDebut, LocalDate dateFin, String exerciceId) {
        DeclarationIS declaration = calculerEtEnregistrerDeclaration(
                dateFin != null ? dateFin.getYear() : LocalDate.now().getYear(),
                dateDebut,
                dateFin,
                exerciceId,
                List.of(),
                List.of()
        );
        Map<String, Object> result = new HashMap<>();
        result.put("dateDebut", declaration.getDateDebut());
        result.put("dateFin", declaration.getDateFin());
        result.put("resultatNet", declaration.getResultatComptable());
        result.put("resultatFiscal", declaration.getResultatFiscal());
        result.put("ca", declaration.getChiffreAffaires());
        result.put("isCalcule", declaration.getIsCalcule());
        result.put("cotisationMinimale", declaration.getCotisationMinimale());
        result.put("isAPayer", declaration.getIsDu());
        result.put("acomptesPayes", declaration.getAcomptesPayes());
        result.put("reliquat", declaration.getReliquat());
        result.put("excedent", declaration.getExcedent());
        return result;
    }

    /**
     * Compatibilite ascendante endpoint historique.
     */
    public List<Map<String, Object>> calculerAcomptes(Integer annee) {
        List<AcompteIS> acomptes = getOrGenerateAcomptes(annee);
        List<Map<String, Object>> result = new ArrayList<>();
        for (AcompteIS a : acomptes) {
            Map<String, Object> line = new HashMap<>();
            line.put("id", a.getId());
            line.put("numero", a.getTrimestre());
            line.put("dateEcheance", a.getDateEcheance());
            line.put("montant", a.getMontant());
            line.put("statut", a.getStatut() != null ? a.getStatut().name() : AcompteIS.StatutAcompte.EN_ATTENTE.name());
            line.put("datePaiement", a.getDatePaiement());
            line.put("montantPaye", a.getMontantPaye());
            result.add(line);
        }
        return result;
    }

    private AcompteIS buildAcompte(Integer annee, int trimestre, LocalDate echeance, double montant, LocalDateTime now) {
        AcompteIS.StatutAcompte statut = echeance.isBefore(LocalDate.now())
                ? AcompteIS.StatutAcompte.EN_RETARD
                : AcompteIS.StatutAcompte.EN_ATTENTE;
        return AcompteIS.builder()
                .annee(annee)
                .trimestre(trimestre)
                .dateEcheance(echeance)
                .montant(montant)
                .montantPaye(0.0)
                .statut(statut)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private List<AcompteIS> refreshAcompteStatus(List<AcompteIS> acomptes) {
        LocalDate today = LocalDate.now();
        boolean changed = false;
        for (AcompteIS a : acomptes) {
            if (a.getStatut() != AcompteIS.StatutAcompte.PAYE
                    && a.getDateEcheance() != null
                    && a.getDateEcheance().isBefore(today)
                    && a.getStatut() != AcompteIS.StatutAcompte.EN_RETARD) {
                a.setStatut(AcompteIS.StatutAcompte.EN_RETARD);
                a.setUpdatedAt(LocalDateTime.now());
                changed = true;
            }
        }
        return changed ? acompteISRepository.saveAll(acomptes) : acomptes;
    }

    private int addLine(Sheet sheet, int rowIndex, String label, Double value) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(nz(value));
        return rowIndex + 1;
    }

    private double sumAjustements(List<DeclarationIS.AjustementFiscal> ajustements) {
        if (ajustements == null) return 0.0;
        return ajustements.stream().mapToDouble(a -> nz(a.getMontant())).sum();
    }

    private double calculISProgressif(double resultatFiscal, ISBaremeConfig cfg) {
        if (resultatFiscal <= 0) {
            return 0.0;
        }
        double s1 = nz(cfg.getSeuilTaux1());
        double s2 = nz(cfg.getSeuilTaux2());
        double t1 = nz(cfg.getTaux1());
        double t2 = nz(cfg.getTaux2());
        double t3 = nz(cfg.getTaux3());
        if (resultatFiscal <= s1) {
            return resultatFiscal * t1;
        }
        if (resultatFiscal <= s2) {
            return (s1 * t1) + ((resultatFiscal - s1) * t2);
        }
        return (s1 * t1) + ((s2 - s1) * t2) + ((resultatFiscal - s2) * t3);
    }

    private ISBaremeConfig getOrCreateBareme() {
        return baremeConfigRepository.findFirstByOrderByUpdatedAtDesc().orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            ISBaremeConfig config = ISBaremeConfig.builder()
                    .seuilTaux1(300000.0)
                    .seuilTaux2(1000000.0)
                    .taux1(0.10)
                    .taux2(0.20)
                    .taux3(0.31)
                    .cotisationMinimaleTaux(0.005)
                    .cotisationMinimaleMinimum(3000.0)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            return baremeConfigRepository.save(config);
        });
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }

    private double nz(Double v) {
        return v != null ? v : 0.0;
    }
}

