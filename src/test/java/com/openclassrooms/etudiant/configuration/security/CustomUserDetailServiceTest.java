package com.openclassrooms.etudiant.configuration.security;

import com.openclassrooms.etudiant.entities.User;
import com.openclassrooms.etudiant.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link CustomUserDetailService} (plan de tests : B-SEC-04).
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailServiceTest {

    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private CustomUserDetailService customUserDetailService;

    @Test
    void loadUserByUsername_knownLogin_returnsUser() {
        // GIVEN
        User user = new User();
        user.setLogin("demo");
        when(userRepository.findByLogin("demo")).thenReturn(Optional.of(user));

        // WHEN
        UserDetails result = customUserDetailService.loadUserByUsername("demo");

        // THEN : l'entité User est elle-même un UserDetails
        assertThat(result.getUsername()).isEqualTo("demo");
    }

    @Test
    void loadUserByUsername_unknownLogin_throwsUsernameNotFound() {
        when(userRepository.findByLogin("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customUserDetailService.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("nobody");
    }
}
