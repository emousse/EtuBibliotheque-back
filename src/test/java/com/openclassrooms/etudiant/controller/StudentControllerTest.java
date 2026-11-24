package com.openclassrooms.etudiant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclassrooms.etudiant.dto.StudentRequestDTO;
import com.openclassrooms.etudiant.entities.Student;
import com.openclassrooms.etudiant.entities.User;
import com.openclassrooms.etudiant.repository.StudentRepository;
import com.openclassrooms.etudiant.repository.UserRepository;
import com.openclassrooms.etudiant.service.JwtService;
import com.openclassrooms.etudiant.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration de {@link StudentController} (plan de tests : B-IT-STU-01 à 09).
 * Toute l'application est démarrée (sécurité JWT, validation, service, JPA) sur une base MySQL
 * jetable lancée par Testcontainers, sur le modèle de UserControllerTest.
 * Un agent est créé avant chaque test et son token est envoyé dans le header « Authorization ».
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class StudentControllerTest {

    private static final String URL = "/api/students";
    private static final String FIRST_NAME = "Ada";
    private static final String LAST_NAME = "Lovelace";
    private static final String EMAIL = "ada@example.com";
    private static final LocalDate BIRTH_DATE = LocalDate.of(2000, 12, 10);

    @Container
    static MySQLContainer<?> mySQLContainer = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private JwtService jwtService;

    private String bearerToken;

    @BeforeEach
    void createAuthenticatedAgent() {
        User agent = new User();
        agent.setFirstName("Agent");
        agent.setLastName("Test");
        agent.setLogin("agent");
        agent.setPassword("password");
        userService.register(agent);
        bearerToken = "Bearer " + jwtService.generateToken(agent);
    }

    @AfterEach
    void cleanDatabase() {
        studentRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- Sécurité ----------

    @Test
    void listStudentsWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Cas nominaux ----------

    @Test
    void createStudentReturnsCreatedStudent() throws Exception {
        // WHEN
        mockMvc.perform(authenticated(post(URL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                // THEN : 201 avec l'id attribué par la base
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.birthDate").value("2000-12-10"));

        assertThat(studentRepository.existsByEmail(EMAIL)).isTrue();
    }

    @Test
    void listStudentsReturnsAllStudents() throws Exception {
        // GIVEN
        saveStudent(EMAIL);
        saveStudent("grace@example.com");

        // WHEN / THEN
        mockMvc.perform(authenticated(get(URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void getStudentByIdReturnsStudent() throws Exception {
        Student student = saveStudent(EMAIL);

        mockMvc.perform(authenticated(get(URL + "/" + student.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(student.getId()))
                .andExpect(jsonPath("$.lastName").value(LAST_NAME));
    }

    @Test
    void updateStudentReturnsUpdatedStudent() throws Exception {
        // GIVEN
        Student student = saveStudent(EMAIL);
        StudentRequestDTO update = new StudentRequestDTO(FIRST_NAME, "King", EMAIL, BIRTH_DATE);

        // WHEN / THEN
        mockMvc.perform(authenticated(put(URL + "/" + student.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("King"));

        assertThat(studentRepository.findById(student.getId()))
                .get().extracting(Student::getLastName).isEqualTo("King");
    }

    @Test
    void deleteStudentReturnsNoContent() throws Exception {
        Student student = saveStudent(EMAIL);

        mockMvc.perform(authenticated(delete(URL + "/" + student.getId())))
                .andExpect(status().isNoContent());

        assertThat(studentRepository.existsById(student.getId())).isFalse();
    }

    // ---------- Règles métier et validation ----------

    @Test
    void createInvalidStudentReturnsBadRequestWithInvalidFields() throws Exception {
        // GIVEN : prénom vide, email invalide, date de naissance dans le futur
        StudentRequestDTO invalid = new StudentRequestDTO("", LAST_NAME, "not-an-email", LocalDate.now().plusDays(1));

        // WHEN / THEN : le message liste les champs rejetés
        mockMvc.perform(authenticated(post(URL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("firstName")))
                .andExpect(jsonPath("$.message", containsString("email")))
                .andExpect(jsonPath("$.message", containsString("birthDate")));
    }

    @Test
    void createStudentWithExistingEmailReturnsBadRequest() throws Exception {
        saveStudent(EMAIL);

        mockMvc.perform(authenticated(post(URL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    void getUnknownStudentReturnsNotFound() throws Exception {
        mockMvc.perform(authenticated(get(URL + "/999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("999")));
    }

    // ---------- Utilitaires ----------

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, bearerToken);
    }

    private StudentRequestDTO validRequest() {
        return new StudentRequestDTO(FIRST_NAME, LAST_NAME, EMAIL, BIRTH_DATE);
    }

    private Student saveStudent(String email) {
        return studentRepository.save(new Student(null, FIRST_NAME, LAST_NAME, email, BIRTH_DATE, null, null));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
