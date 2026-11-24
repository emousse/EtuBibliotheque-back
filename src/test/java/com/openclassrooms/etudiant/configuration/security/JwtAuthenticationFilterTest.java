package com.openclassrooms.etudiant.configuration.security;

import com.openclassrooms.etudiant.service.JwtService;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link JwtAuthenticationFilter} (plan de tests : B-SEC-01 à 03).
 * Les objets « Mock » de spring-test simulent la requête, la réponse et la suite de la chaîne de filtres ;
 * JwtService et UserDetailsService sont simulés avec Mockito.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "valid.jwt.token";
    private static final String LOGIN = "demo";

    @Mock
    private JwtService jwtService;
    @Mock
    private UserDetailsService userDetailsService;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
    }

    @AfterEach
    void clearSecurityContext() {
        // Le contexte de sécurité est global au thread : on le vide pour isoler les tests
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthorizationHeader_continuesWithoutAuthentication() throws Exception {
        // WHEN
        filter.doFilter(request, response, filterChain);

        // THEN : la requête continue, personne n'est authentifié, le token n'est même pas lu
        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void validBearerToken_authenticatesUser() throws Exception {
        // GIVEN
        UserDetails user = User.withUsername(LOGIN).password("unused").build();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
        when(jwtService.extractUsername(TOKEN)).thenReturn(LOGIN);
        when(userDetailsService.loadUserByUsername(LOGIN)).thenReturn(user);
        when(jwtService.isTokenValid(TOKEN, user)).thenReturn(true);

        // WHEN
        filter.doFilter(request, response, filterChain);

        // THEN : l'utilisateur est authentifié pour la suite de la requête
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(user);
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @Test
    void invalidBearerToken_continuesWithoutAuthentication() throws Exception {
        // GIVEN : jjwt rejette le token
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi");
        when(jwtService.extractUsername(anyString())).thenThrow(new MalformedJwtException("invalid"));

        // WHEN
        filter.doFilter(request, response, filterChain);

        // THEN : pas d'exception propagée, pas d'authentification ; Spring Security renverra 401
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(filterChain.getRequest()).isSameAs(request);
        verifyNoInteractions(userDetailsService);
    }
}
