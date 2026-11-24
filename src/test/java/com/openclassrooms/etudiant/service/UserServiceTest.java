package com.openclassrooms.etudiant.service;

import com.openclassrooms.etudiant.entities.User;
import com.openclassrooms.etudiant.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class UserServiceTest {
    private static final String FIRST_NAME = "John";
    private static final String LAST_NAME = "Doe";
    private static final String LOGIN = "LOGIN";
    private static final String PASSWORD = "PASSWORD";
    private static final String HASHED_PASSWORD = "$2a$10$hashedPassword";
    private static final String TOKEN = "jwt-token";
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @InjectMocks
    private UserService userService;

    @Test
    public void test_create_null_user_throws_IllegalArgumentException() {
        // GIVEN

        // THEN
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> userService.register(null));
    }

    @Test
    public void test_create_already_exist_user_throws_IllegalArgumentException() {
        // GIVEN
        User user = new User();
        user.setFirstName(FIRST_NAME);
        user.setLastName(LAST_NAME);
        user.setLogin(LOGIN);
        user.setPassword(PASSWORD);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD);
        when(userRepository.findByLogin(any())).thenReturn(Optional.of(user));

        // THEN
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> userService.register(user));
    }

    @Test
    public void test_create_user() {
        // GIVEN
        User user = new User();
        user.setFirstName(FIRST_NAME);
        user.setLastName(LAST_NAME);
        user.setLogin(LOGIN);
        user.setPassword(PASSWORD);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD);
        when(userRepository.findByLogin(any())).thenReturn(Optional.empty());

        // WHEN
        userService.register(user);

        // THEN
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue()).isEqualTo(user);
    }

    // ---------- login (plan de tests : B-USR-01 à 03) ----------

    @Test
    public void test_login_valid_credentials_returns_token() {
        // GIVEN : utilisateur en base avec son mot de passe haché
        User user = new User();
        user.setLogin(LOGIN);
        user.setPassword(HASHED_PASSWORD);
        when(userRepository.findByLogin(LOGIN)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASHED_PASSWORD)).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn(TOKEN);

        // WHEN
        String token = userService.login(LOGIN, PASSWORD);

        // THEN : le mot de passe saisi est comparé au hash stocké, et le token du JwtService est renvoyé
        assertThat(token).isEqualTo(TOKEN);
        verify(passwordEncoder).matches(PASSWORD, HASHED_PASSWORD);
    }

    @Test
    public void test_login_wrong_password_throws_IllegalArgumentException() {
        // GIVEN
        User user = new User();
        user.setLogin(LOGIN);
        user.setPassword(HASHED_PASSWORD);
        when(userRepository.findByLogin(LOGIN)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", HASHED_PASSWORD)).thenReturn(false);

        // THEN : aucun token n'est généré
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> userService.login(LOGIN, "wrong"));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    public void test_login_unknown_user_throws_IllegalArgumentException() {
        // GIVEN
        when(userRepository.findByLogin(LOGIN)).thenReturn(Optional.empty());

        // THEN
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> userService.login(LOGIN, PASSWORD));
        verify(jwtService, never()).generateToken(any());
    }
}
