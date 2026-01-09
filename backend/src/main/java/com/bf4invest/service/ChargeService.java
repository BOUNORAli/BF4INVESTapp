package com.bf4invest.service;

import com.bf4invest.model.Charge;
import com.bf4invest.repository.ChargeRepository;
import com.bf4invest.util.NumberUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeService {

    private final ChargeRepository chargeRepository;
    private final AuditService auditService;
    private final SoldeService soldeService;
    private final ComptabiliteService comptabiliteService;

    public List<Charge> findAll(
            LocalDate from,
            LocalDate to,
            String statut,
            Boolean imposable,
            String q
    ) {
        List<Charge> charges = chargeRepository.findAll();

        // Filtres
        if (from != null) {
            charges = charges.stream()
                    .filter(c -> c.getDateEcheance() != null && !c.getDateEcheance().isBefore(from))
                    .toList();
        }
        if (to != null) {
            charges = charges.stream()
                    .filter(c -> c.getDateEcheance() != null && !c.getDateEcheance().isAfter(to))
                    .toList();
        }
        if (statut != null && !statut.trim().isEmpty()) {
            String s = statut.trim().toUpperCase(Locale.ROOT);
            charges = charges.stream()
                    .filter(c -> c.getStatut() != null && c.getStatut().equalsIgnoreCase(s))
                    .toList();
        }
        if (imposable != null) {
            charges = charges.stream()
                    .filter(c -> c.getImposable() != null && c.getImposable().equals(imposable))
                    .toList();
        }
        if (q != null && !q.trim().isEmpty()) {
            String query = q.trim().toLowerCase(Locale.ROOT);
            charges = charges.stream()
                    .filter(c ->
                            (c.getLibelle() != null && c.getLibelle().toLowerCase(Locale.ROOT).contains(query)) ||
                            (c.getCategorie() != null && c.getCategorie().toLowerCase(Locale.ROOT).contains(query)) ||
                            (c.getNotes() != null && c.getNotes().toLowerCase(Locale.ROOT).contains(query))
                    )
                    .toList();
        }

        // Tri: échéance desc, puis updatedAt desc
        return charges.stream()
                .sorted(Comparator
                        .comparing((Charge c) -> c.getDateEcheance() != null ? c.getDateEcheance() : LocalDate.MIN)
                        .reversed()
                        .thenComparing(c -> c.getUpdatedAt() != null ? c.getUpdatedAt() : LocalDateTime.MIN, Comparator.reverseOrder())
                )
                .toList();
    }

    public Optional<Charge> findById(String id) {
        return chargeRepository.findById(id);
    }

    public Charge create(Charge charge) {
        if (charge == null) {
            throw new IllegalArgumentException("Charge invalide");
        }
        if (charge.getMontant() == null || charge.getMontant() <= 0) {
            throw new IllegalArgumentException("Le montant doit être positif");
        }
        if (charge.getLibelle() == null || charge.getLibelle().trim().isEmpty()) {
            throw new IllegalArgumentException("Le libellé est requis");
        }
        if (charge.getDateEcheance() == null) {
            throw new IllegalArgumentException("La date d'échéance est requise");
        }

        // Defaults
        // Toujours créer en PREVUE (le passage en PAYEE doit se faire via /payer pour impacter la trésorerie)
        charge.setStatut("PREVUE");
        if (charge.getImposable() == null) {
            charge.setImposable(Boolean.TRUE);
        }
        // Si imposable est true mais tauxImposition n'est pas défini, définir une valeur par défaut (20%)
        if (Boolean.TRUE.equals(charge.getImposable()) && charge.getTauxImposition() == null) {
            charge.setTauxImposition(0.20); // 20% par défaut
        }
        // Si imposable est false, s'assurer que tauxImposition est null
        if (Boolean.FALSE.equals(charge.getImposable())) {
            charge.setTauxImposition(null);
        }
        charge.setDatePaiement(null);
        charge.setCreatedAt(LocalDateTime.now());
        charge.setUpdatedAt(LocalDateTime.now());

        Charge saved = chargeRepository.save(charge);

        auditService.logCreate("Charge", saved.getId(),
                "Charge créée: " + saved.getLibelle() + " - " + saved.getMontant() + " MAD (" +
                        (Boolean.TRUE.equals(saved.getImposable()) ? "imposable" : "non imposable") + ")");

        return saved;
    }

    public Charge update(String id, Charge updated) {
        Charge existing = chargeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Charge introuvable"));

        // Si déjà payée, bloquer les changements financiers
        boolean isPayee = "PAYEE".equalsIgnoreCase(existing.getStatut());
        if (isPayee) {
            // Autoriser uniquement la modification de notes/libellé/catégorie
            String oldValue = existing.getLibelle() + " | " + existing.getCategorie() + " | " + (existing.getNotes() != null ? existing.getNotes() : "");
            if (updated.getLibelle() != null) existing.setLibelle(updated.getLibelle());
            if (updated.getCategorie() != null) existing.setCategorie(updated.getCategorie());
            existing.setNotes(updated.getNotes());
            existing.setUpdatedAt(LocalDateTime.now());

            Charge saved = chargeRepository.save(existing);
            String newValue = saved.getLibelle() + " | " + saved.getCategorie() + " | " + (saved.getNotes() != null ? saved.getNotes() : "");
            auditService.logUpdate("Charge", id, oldValue, newValue);
            return saved;
        }

        String oldValue = existing.getLibelle() + " - " + existing.getMontant() + " - " + existing.getDateEcheance() + " - " + existing.getStatut();

        if (updated.getLibelle() != null) existing.setLibelle(updated.getLibelle());
        if (updated.getCategorie() != null) existing.setCategorie(updated.getCategorie());
        if (updated.getMontant() != null) existing.setMontant(NumberUtils.roundTo2Decimals(updated.getMontant()));
        if (updated.getDateEcheance() != null) existing.setDateEcheance(updated.getDateEcheance());
        if (updated.getImposable() != null) existing.setImposable(updated.getImposable());
        // Gérer tauxImposition : si imposable devient false, mettre tauxImposition à null
        // Si imposable devient true et tauxImposition n'est pas défini, mettre 20% par défaut
        if (updated.getImposable() != null) {
            if (Boolean.FALSE.equals(updated.getImposable())) {
                existing.setTauxImposition(null);
            } else if (Boolean.TRUE.equals(updated.getImposable()) && updated.getTauxImposition() == null && existing.getTauxImposition() == null) {
                existing.setTauxImposition(0.20); // 20% par défaut
            } else if (updated.getTauxImposition() != null) {
                existing.setTauxImposition(updated.getTauxImposition());
            }
        } else if (updated.getTauxImposition() != null) {
            existing.setTauxImposition(updated.getTauxImposition());
        }
        // Ne pas permettre de forcer PAYEE via update: utiliser l'endpoint /payer pour créer l'historique de trésorerie
        existing.setNotes(updated.getNotes());

        existing.setUpdatedAt(LocalDateTime.now());
        Charge saved = chargeRepository.save(existing);

        String newValue = saved.getLibelle() + " - " + saved.getMontant() + " - " + saved.getDateEcheance() + " - " + saved.getStatut();
        auditService.logUpdate("Charge", id, oldValue, newValue);

        return saved;
    }

    public void delete(String id) {
        Charge existing = chargeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Charge introuvable"));

        if ("PAYEE".equalsIgnoreCase(existing.getStatut())) {
            throw new IllegalStateException("Impossible de supprimer une charge payée");
        }

        auditService.logDelete("Charge", id, "Charge supprimée: " + existing.getLibelle());
        chargeRepository.deleteById(id);
    }

    public Charge marquerPayee(String id, LocalDate datePaiement) {
        Charge existing = chargeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Charge introuvable"));

        if ("PAYEE".equalsIgnoreCase(existing.getStatut())) {
            return existing; // idempotent
        }

        String oldValue = existing.getStatut() + " | " + existing.getDatePaiement();

        existing.setStatut("PAYEE");
        existing.setDatePaiement(datePaiement != null ? datePaiement : LocalDate.now());
        existing.setUpdatedAt(LocalDateTime.now());

        Charge saved = chargeRepository.save(existing);

        String newValue = saved.getStatut() + " | " + saved.getDatePaiement();
        auditService.logUpdate("Charge", id, oldValue, newValue);

        // Impact trésorerie: HistoriqueSolde + MAJ solde global
        if (saved.getMontant() != null && saved.getMontant() > 0) {
            soldeService.ajouterCharge(
                    saved.getMontant(),
                    saved.getImposable(),
                    saved.getId(),
                    saved.getLibelle(),
                    saved.getDatePaiement()
            );
        } else {
            log.warn("Charge {} marquée PAYEE mais montant invalide: {}", saved.getId(), saved.getMontant());
        }

        // Générer l'écriture comptable
        try {
            comptabiliteService.genererEcritureCharge(saved);
        } catch (Exception e) {
            log.warn("Erreur lors de la génération de l'écriture comptable pour charge {}: {}", saved.getId(), e.getMessage());
        }

        return saved;
    }
}


