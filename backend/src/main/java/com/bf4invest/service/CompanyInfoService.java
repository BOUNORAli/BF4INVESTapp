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
                            .raisonSociale("BF4 INVEST SARL")
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
                    info.setCreatedAt(LocalDateTime.now());
                    info.setUpdatedAt(LocalDateTime.now());
                    return repository.save(info);
                });

        existing.setRaisonSociale(info.getRaisonSociale());
        existing.setVille(info.getVille());
        existing.setIce(info.getIce());
        existing.setCapital(info.getCapital());
        existing.setTelephone(info.getTelephone());
        existing.setRc(info.getRc());
        existing.setIfFiscal(info.getIfFiscal());
        existing.setTp(info.getTp());
        existing.setUpdatedAt(LocalDateTime.now());

        return repository.save(existing);
    }
}


