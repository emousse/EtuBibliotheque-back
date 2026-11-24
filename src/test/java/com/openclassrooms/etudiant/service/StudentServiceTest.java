package com.openclassrooms.etudiant.service;

import com.openclassrooms.etudiant.dto.StudentRequestDTO;
import com.openclassrooms.etudiant.dto.StudentResponseDTO;
import com.openclassrooms.etudiant.entities.Student;
import com.openclassrooms.etudiant.handler.ResourceNotFoundException;
import com.openclassrooms.etudiant.mapper.StudentDtoMapper;
import com.openclassrooms.etudiant.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link StudentService} (plan de tests : B-STU-01 à 10).
 * Le repository et le mapper sont simulés avec Mockito : seule la logique métier du service est testée.
 * MockitoExtension (plutôt que SpringExtension) ne démarre pas Spring et signale les stubs inutilisés.
 */
@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    private static final Long ID = 1L;
    private static final Long UNKNOWN_ID = 99L;
    private static final String FIRST_NAME = "Ada";
    private static final String LAST_NAME = "Lovelace";
    private static final String EMAIL = "ada@example.com";
    private static final LocalDate BIRTH_DATE = LocalDate.of(2000, 12, 10);

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private StudentDtoMapper studentDtoMapper;
    @InjectMocks
    private StudentService studentService;

    private StudentRequestDTO request;
    private Student student;
    private StudentResponseDTO response;

    @BeforeEach
    void setUp() {
        request = new StudentRequestDTO(FIRST_NAME, LAST_NAME, EMAIL, BIRTH_DATE);
        student = new Student(ID, FIRST_NAME, LAST_NAME, EMAIL, BIRTH_DATE, null, null);
        response = new StudentResponseDTO(ID, FIRST_NAME, LAST_NAME, EMAIL, BIRTH_DATE);
    }

    // ---------- Cas nominaux ----------

    @Test
    void findAll_returnsAllStudentsAsDtos() {
        // GIVEN
        Student other = new Student(2L, "Grace", "Hopper", "grace@example.com", BIRTH_DATE, null, null);
        List<StudentResponseDTO> dtos = List.of(response,
                new StudentResponseDTO(2L, "Grace", "Hopper", "grace@example.com", BIRTH_DATE));
        when(studentRepository.findAll()).thenReturn(List.of(student, other));
        when(studentDtoMapper.toDtoList(List.of(student, other))).thenReturn(dtos);

        // WHEN
        List<StudentResponseDTO> result = studentService.findAll();

        // THEN
        assertThat(result).hasSize(2).isEqualTo(dtos);
    }

    @Test
    void findById_existingId_returnsStudent() {
        when(studentRepository.findById(ID)).thenReturn(Optional.of(student));
        when(studentDtoMapper.toDto(student)).thenReturn(response);

        assertThat(studentService.findById(ID)).isEqualTo(response);
    }

    @Test
    void create_newEmail_savesAndReturnsStudent() {
        // GIVEN
        when(studentRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(studentDtoMapper.toEntity(request)).thenReturn(student);
        when(studentRepository.save(student)).thenReturn(student);
        when(studentDtoMapper.toDto(student)).thenReturn(response);

        // WHEN
        StudentResponseDTO result = studentService.create(request);

        // THEN
        assertThat(result).isEqualTo(response);
        verify(studentRepository).save(student);
    }

    @Test
    void update_existingStudent_updatesAndSaves() {
        // GIVEN
        StudentRequestDTO update = new StudentRequestDTO(FIRST_NAME, "King", EMAIL, BIRTH_DATE);
        StudentResponseDTO updated = new StudentResponseDTO(ID, FIRST_NAME, "King", EMAIL, BIRTH_DATE);
        when(studentRepository.findById(ID)).thenReturn(Optional.of(student));
        when(studentRepository.existsByEmailAndIdNot(EMAIL, ID)).thenReturn(false);
        when(studentRepository.save(student)).thenReturn(student);
        when(studentDtoMapper.toDto(student)).thenReturn(updated);

        // WHEN
        StudentResponseDTO result = studentService.update(ID, update);

        // THEN : le mapper reporte les nouvelles valeurs sur l'entité existante, puis elle est sauvegardée
        verify(studentDtoMapper).updateEntity(update, student);
        verify(studentRepository).save(student);
        assertThat(result.getLastName()).isEqualTo("King");
    }

    @Test
    void delete_existingStudent_deletesIt() {
        when(studentRepository.findById(ID)).thenReturn(Optional.of(student));

        studentService.delete(ID);

        verify(studentRepository).delete(student);
    }

    // ---------- Règles métier ----------

    @Test
    void create_emailAlreadyUsed_throwsAndDoesNotSave() {
        when(studentRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> studentService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(EMAIL);
        verify(studentRepository, never()).save(any());
    }

    @Test
    void create_nullStudent_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> studentService.create(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findById_unknownId_throwsResourceNotFound() {
        when(studentRepository.findById(UNKNOWN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.findById(UNKNOWN_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(UNKNOWN_ID));
    }

    @Test
    void update_emailUsedByAnotherStudent_throwsAndDoesNotSave() {
        when(studentRepository.findById(ID)).thenReturn(Optional.of(student));
        when(studentRepository.existsByEmailAndIdNot(EMAIL, ID)).thenReturn(true);

        assertThatThrownBy(() -> studentService.update(ID, request))
                .isInstanceOf(IllegalArgumentException.class);
        verify(studentRepository, never()).save(any());
    }

    @Test
    void update_unknownId_throwsResourceNotFound() {
        when(studentRepository.findById(UNKNOWN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.update(UNKNOWN_ID, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_unknownId_throwsAndDeletesNothing() {
        when(studentRepository.findById(UNKNOWN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.delete(UNKNOWN_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(studentRepository, never()).delete(any());
    }
}
