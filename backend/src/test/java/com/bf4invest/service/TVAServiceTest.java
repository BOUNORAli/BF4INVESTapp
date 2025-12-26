package com.bf4invest.service;

import com.bf4invest.model.*;
import com.bf4invest.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TVAServiceTest {

    @Mock
    private DeclarationTVARepository declarationRepository;

    @Mock
    private EcritureComptableRepository ecritureRepository;

    @Mock
    private ComptabiliteService comptabiliteService;

    @Mock
    private PaiementRepository paiementRepository;

    @Mock
    private FactureVenteRepository factureVenteRepository;

    @Mock
    private FactureAchatRepository factureAchatRepository;

    @InjectMocks
    private TVAService tvaService;

    @Test
    void testCalculerDeclarationTVA_AvecFactureVente() {
        // Given
        Integer mois = 1;
        Integer annee = 2024;
        
        FactureVente facture = FactureVente.builder()
                .id("facture-1")
                .numeroFactureVente("FV-2024-001")
                .totalTVA(200.0)
                .tvaRate(0.20)
                .build();

        EcritureComptable ecriture = EcritureComptable.builder()
                .id("ecriture-1")
                .dateEcriture(LocalDate.of(2024, 1, 15))
                .pieceJustificativeType("FACTURE_VENTE")
                .pieceJustificativeId("facture-1")
                .build();

        when(declarationRepository.findByMoisAndAnnee(mois, annee)).thenReturn(Optional.empty());
        when(ecritureRepository.findByDateEcritureBetween(any(), any())).thenReturn(java.util.List.of(ecriture));
        when(factureVenteRepository.findById("facture-1")).thenReturn(Optional.of(facture));

        // When
        DeclarationTVA declaration = tvaService.calculerDeclarationTVA(mois, annee);

        // Then
        assertNotNull(declaration);
        assertEquals(mois, declaration.getMois());
        assertEquals(annee, declaration.getAnnee());
        assertEquals(DeclarationTVA.StatutDeclaration.BROUILLON, declaration.getStatut());
    }

    @Test
    void testCalculerDeclarationTVA_AvecFactureAchat() {
        // Given
        Integer mois = 1;
        Integer annee = 2024;
        
        FactureAchat facture = FactureAchat.builder()
                .id("facture-achat-1")
                .numeroFactureAchat("FA-2024-001")
                .totalTVA(100.0)
                .tvaRate(0.20)
                .build();

        EcritureComptable ecriture = EcritureComptable.builder()
                .id("ecriture-1")
                .dateEcriture(LocalDate.of(2024, 1, 15))
                .pieceJustificativeType("FACTURE_ACHAT")
                .pieceJustificativeId("facture-achat-1")
                .build();

        when(declarationRepository.findByMoisAndAnnee(mois, annee)).thenReturn(Optional.empty());
        when(ecritureRepository.findByDateEcritureBetween(any(), any())).thenReturn(java.util.List.of(ecriture));
        when(factureAchatRepository.findById("facture-achat-1")).thenReturn(Optional.of(facture));

        // When
        DeclarationTVA declaration = tvaService.calculerDeclarationTVA(mois, annee);

        // Then
        assertNotNull(declaration);
        assertEquals(mois, declaration.getMois());
        assertEquals(annee, declaration.getAnnee());
    }
}

