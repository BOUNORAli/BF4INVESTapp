package com.bf4invest.service;

import com.bf4invest.model.ExerciceComptable;
import com.bf4invest.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComptabiliteServiceTest {

    @Mock
    private CompteComptableRepository compteRepository;

    @Mock
    private ExerciceComptableRepository exerciceRepository;

    @Mock
    private EcritureComptableRepository ecritureRepository;

    @Mock
    private FactureVenteRepository factureVenteRepository;

    @Mock
    private FactureAchatRepository factureAchatRepository;

    @Mock
    private PaiementRepository paiementRepository;

    @Mock
    private ChargeRepository chargeRepository;

    @InjectMocks
    private ComptabiliteService comptabiliteService;

    @Test
    void testCreateExercice_Success() {
        // Given
        ExerciceComptable exercice = ExerciceComptable.builder()
                .dateDebut(LocalDate.of(2024, 1, 1))
                .dateFin(LocalDate.of(2024, 12, 31))
                .statut(ExerciceComptable.StatutExercice.OUVERT)
                .build();

        when(exerciceRepository.findByCode(any())).thenReturn(Optional.empty());
        when(exerciceRepository.findAll()).thenReturn(new ArrayList<>());
        when(exerciceRepository.save(any(ExerciceComptable.class))).thenAnswer(invocation -> {
            ExerciceComptable ex = invocation.getArgument(0);
            ex.setId("exercice-1");
            return ex;
        });

        // When
        ExerciceComptable created = comptabiliteService.createExercice(exercice);

        // Then
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("2024", created.getCode());
        assertEquals(LocalDate.of(2024, 1, 1), created.getDateDebut());
        assertEquals(LocalDate.of(2024, 12, 31), created.getDateFin());
        verify(exerciceRepository, times(1)).save(any(ExerciceComptable.class));
    }

    @Test
    void testCreateExercice_ThrowsException_WhenDatesInvalid() {
        // Given
        ExerciceComptable exercice = ExerciceComptable.builder()
                .dateDebut(LocalDate.of(2024, 12, 31))
                .dateFin(LocalDate.of(2024, 1, 1)) // Date fin avant date début
                .build();

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            comptabiliteService.createExercice(exercice);
        });
    }

    @Test
    void testCreateExercice_ThrowsException_WhenOverlapping() {
        // Given
        ExerciceComptable newExercice = ExerciceComptable.builder()
                .dateDebut(LocalDate.of(2024, 6, 1))
                .dateFin(LocalDate.of(2024, 12, 31))
                .build();

        ExerciceComptable existing = ExerciceComptable.builder()
                .id("existing-1")
                .code("2024")
                .dateDebut(LocalDate.of(2024, 1, 1))
                .dateFin(LocalDate.of(2024, 12, 31))
                .build();

        when(exerciceRepository.findByCode(any())).thenReturn(Optional.empty());
        when(exerciceRepository.findAll()).thenReturn(List.of(existing));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            comptabiliteService.createExercice(newExercice);
        });
    }
}
