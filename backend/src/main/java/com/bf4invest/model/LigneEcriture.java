package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LigneEcriture {
    private String compteCode; // Code du compte (ex: 6111)
    private String compteLibelle; // Libellé du compte
    private Double debit; // Montant débit (null si crédit)
    private Double credit; // Montant crédit (null si débit)
    private String libelle; // Libellé de la ligne
}

