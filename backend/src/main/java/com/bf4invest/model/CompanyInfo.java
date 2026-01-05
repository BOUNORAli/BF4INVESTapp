package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Informations légales de la société utilisées notamment dans le footer des PDFs.
 * Exemple : ICE, capital social, téléphone, RC, IF, TP, etc.
 */
@Document(collection = "company_info")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyInfo {

    @Id
    private String id;

    /** Raison sociale de la société (ex: BF4 INVEST SARL) */
    private String raisonSociale;

    /** Ville utilisée dans le RC (ex: Meknes) */
    private String ville;

    /** ICE de la société */
    private String ice;

    /** Capital social affiché (format texte pour garder la mise en forme) */
    private String capital;

    /** Capital actuel de l'entreprise (valeur numérique pour les calculs) */
    private Double capitalActuel;

    /** Téléphone principal */
    private String telephone;

    /** Numéro de RC */
    private String rc;

    /** Identifiant fiscal */
    private String ifFiscal;

    /** Numéro de taxe professionnelle */
    private String tp;

    /** Banque de l'entreprise (ex: ATTIJARI WAFABANK) */
    private String banque;

    /** Agence bancaire (ex: CENTRE D AFFAIRE MEKNES) */
    private String agence;

    /** RIB ou Numéro de compte (ex: 000542H000001759) */
    private String rib;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}


