package com.bellidental.identity.application;

import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.persistence.entity.Role;
import com.bellidental.persistence.entity.User;
import com.bellidental.persistence.entity.UserRole;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.security.CurrentUser;
import com.bellidental.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private com.bellidental.security.UserAuthorities userAuthorities;

    @InjectMocks
    private AuthService authService;

    private User user;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        Role role = Role.builder().code("ADMIN").build();
        user = User.builder()
                .id(UUID.randomUUID())
                .email("test@bellidental.local")
                .passwordHash("hash")
                .active(true)
                .isSystem(false)
                .build();
        
        UserRole ur = UserRole.builder().role(role).user(user).build();
        user.setUserRoles(Set.of(ur));

        loginRequest = new LoginRequest("test@bellidental.local", "password");
    }

    @Test
    void login_userInactive_throwsExceptionWithGenericMessage() {
        user.setActive(false);
        when(userRepository.findByEmailAndDeletedAtIsNull("test@bellidental.local")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Credenciales inválidas");
    }

    @Test
    void login_userDeleted_throwsExceptionWithGenericMessage() {
        user.softDelete();
        when(userRepository.findByEmailAndDeletedAtIsNull("test@bellidental.local")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Credenciales inválidas");
    }

    @Test
    void login_userIsSystem_throwsExceptionWithGenericMessage() {
        user.setSystem(true);
        when(userRepository.findByEmailAndDeletedAtIsNull("test@bellidental.local")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Credenciales inválidas");
    }

    @Test
    void login_validCredentials_updatesLastLoginAtAndReturnsRoles() {
        when(userRepository.findByEmailAndDeletedAtIsNull("test@bellidental.local")).thenReturn(Optional.of(user));
        when(professionalRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(jwtService.generateToken(user, null)).thenReturn("mock.jwt.token");
        when(jwtService.getExpirationTime()).thenReturn(3600L);
        when(userAuthorities.getRoles(user)).thenReturn(java.util.List.of("ADMIN"));

        AuthResponse response = authService.login(loginRequest);

        assertThat(response.accessToken()).isEqualTo("mock.jwt.token");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.roles()).containsExactly("ADMIN");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getLastLoginAt()).isNotNull();
    }
}
