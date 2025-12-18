package com.bf4invest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Paramètres de calcul utilisés dans les formules comptables.
 * Correspond aux valeurs des cellules $D$2127, $E$2123, $E$2125, $E$2124 de l'Excel.
 */
@Document(collection = "parametres_calcul")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParametresCalcul {
    @Id
    private String id;
    
    /**
     * Code de clôture utilisé dans la formule Bilan (équivalent $D$2127)
     * Les lignes avec ce code dans la colonne D sont exclues du calcul du bilan
     */
    private String codeDCloture;
    
    /**
     * Code d'exclusion 1 utilisé dans la formule Bilan (équivalent $E$2123)
     * Les lignes avec ce code dans la colonne E sont exclues du calcul du bilan
     */
    private String codeEExclu1;
    
    /**
     * Code d'exclusion 2 utilisé dans la formule Bilan (équivalent $E$2125)
     */
    private String codeEExclu2;
    
    /**
     * Code d'exclusion 3 utilisé dans la formule Bilan (équivalent $E$2124)
     */
    private String codeEExclu3;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

