package com.bf4invest.service;

import com.bf4invest.model.CompanyInfo;
import com.bf4invest.repository.CompanyInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Service pour gérer les informations légales de la société (utilisées notamment dans les footers PDF).
 */
@Service
@RequiredArgsConstructor
public class CompanyInfoService {

    private final CompanyInfoRepository repository;

    /**
     * Récupère les informations société.
     * Si aucune entrée n'existe, crée un document avec les valeurs actuelles codées en dur
     * pour conserver le comportement existant.
     */
    public CompanyInfo getCompanyInfo() {
        return repository.findFirstByOrderByUpdatedAtDesc()
                .orElseGet(() -> {
                    CompanyInfo defaults = CompanyInfo.builder()
                            .raisonSociale("STE BF4 INVEST")
                            .ville("Meknes")
                            .ice("002889872000062")
                            .capital("2.000.000,00")
                            .telephone("06 61 51 11 91")
                            .rc("54287")
                            .ifFiscal("50499801")
                            .tp("17101980")
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return repository.save(defaults);
                });
    }

    /**
     * Met à jour (ou crée) les informations société.
     */
    public CompanyInfo saveCompanyInfo(CompanyInfo info) {
        CompanyInfo existing = repository.findFirstByOrderByUpdatedAtDesc()
                .orElseGet(() -> {
                    CompanyInfo newInfo = new CompanyInfo();
                    newInfo.setCreatedAt(LocalDateTime.now());
                    newInfo.setUpdatedAt(LocalDateTime.now());
                    return newInfo;
                });

        // Mettre à jour tous les champs, même s'ils sont null ou vides
        if (info.getRaisonSociale() != null) {
            existing.setRaisonSociale(info.getRaisonSociale());
        }
        if (info.getVille() != null) {
            existing.setVille(info.getVille());
        }
        if (info.getIce() != null) {
            existing.setIce(info.getIce());
        }
        if (info.getCapital() != null) {
            existing.setCapital(info.getCapital());
        }
        if (info.getCapitalActuel() != null) {
            existing.setCapitalActuel(info.getCapitalActuel());
        }
        if (info.getTelephone() != null) {
            existing.setTelephone(info.getTelephone());
        }
        if (info.getRc() != null) {
            existing.setRc(info.getRc());
        }
        if (info.getIfFiscal() != null) {
            existing.setIfFiscal(info.getIfFiscal());
        }
        if (info.getTp() != null) {
            existing.setTp(info.getTp());
        }
        if (info.getBanque() != null) {
            existing.setBanque(info.getBanque());
        }
        if (info.getAgence() != null) {
            existing.setAgence(info.getAgence());
        }
        if (info.getRib() != null) {
            existing.setRib(info.getRib());
        }
        existing.setUpdatedAt(LocalDateTime.now());

        return repository.save(existing);
    }

    /**
     * Met à jour le capital actuel de l'entreprise.
     * @param montant Le montant à ajouter au capital actuel (peut être négatif pour une diminution)
     */
    public CompanyInfo updateCapitalActuel(Double montant) {
        CompanyInfo existing = getCompanyInfo();
        Double capitalActuel = existing.getCapitalActuel() != null ? existing.getCapitalActuel() : 0.0;
        capitalActuel += montant;
        existing.setCapitalActuel(capitalActuel);
        existing.setUpdatedAt(LocalDateTime.now());
        return repository.save(existing);
    }

    /**
     * Initialise le capital actuel avec une valeur donnée.
     */
    public CompanyInfo setCapitalActuel(Double montant) {
        CompanyInfo existing = getCompanyInfo();
        existing.setCapitalActuel(montant);
        existing.setUpdatedAt(LocalDateTime.now());
        return repository.save(existing);
    }
}


