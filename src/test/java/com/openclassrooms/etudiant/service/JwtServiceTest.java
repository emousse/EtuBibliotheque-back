package com.openclassrooms.etudiant.service;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires de {@link JwtService}.
 * Le service n'a aucune dépendance : il est instancié directement avec une clé de test,
 * sans Spring ni Mockito (plan de tests : B-JWT-01 à 06).
 */
class JwtServiceTest {

    // Deux clés HMAC de 256 bits encodées en base64, réservées aux tests
    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMzJieXRlcyE=";
    private static final String OTHER_SECRET = "YW5vdGhlci1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMzI=";
    private static final long ONE_HOUR_MS = 3_600_000L;
    private static final String LOGIN = "demo";

    private JwtService jwtService;
    private UserDetails demoUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, ONE_HOUR_MS);
        demoUser = User.withUsername(LOGIN).password("unused").build();
    }

    @Test
    void generateToken_returnsJwtWithThreeParts() {
        // WHEN
        String token = jwtService.generateToken(demoUser);

        // THEN : en-tête.contenu.signature
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void extractUsername_returnsTokenSubject() {
        // GIVEN
        String token = jwtService.generateToken(demoUser);

        // WHEN / THEN
        assertThat(jwtService.extractUsername(token)).isEqualTo(LOGIN);
    }

    @Test
    void isTokenValid_sameUser_returnsTrue() {
        String token = jwtService.generateToken(demoUser);

        assertThat(jwtService.isTokenValid(token, demoUser)).isTrue();
    }

    @Test
    void isTokenValid_otherUser_returnsFalse() {
        // GIVEN : token émis pour « demo », vérifié pour un autre utilisateur
        String token = jwtService.generateToken(demoUser);
        UserDetails otherUser = User.withUsername("other").password("unused").build();

        // WHEN / THEN
        assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
    }

    @Test
    void extractUsername_tokenSignedWithAnotherKey_throwsJwtException() {
        // GIVEN : token signé par un autre serveur (clé différente) = token falsifié
        String forgedToken = new JwtService(OTHER_SECRET, ONE_HOUR_MS).generateToken(demoUser);

        // WHEN / THEN : la signature ne correspond pas à notre clé
        assertThatThrownBy(() -> jwtService.extractUsername(forgedToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractUsername_expiredToken_throwsExpiredJwtException() {
        // GIVEN : service dont les tokens expirent dès leur création
        JwtService expiringJwtService = new JwtService(SECRET, -1_000L);
        String expiredToken = expiringJwtService.generateToken(demoUser);

        // WHEN / THEN
        assertThatThrownBy(() -> jwtService.extractUsername(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
