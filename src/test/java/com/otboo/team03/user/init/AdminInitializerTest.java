package com.otboo.team03.user.init;

import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.entity.UserRole;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminInitializer adminInitializer;

    @BeforeEach
    void setUp() {
        adminInitializer = new AdminInitializer(
                userRepository, passwordEncoder, "admin@example.com", "adminPassword123!", "관리자");
    }

    @Test
    void initialize_adminNotExists_createsAdmin() {
        // given
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("adminPassword123!")).thenReturn("encodedPassword");

        // when
        adminInitializer.initialize();

        // then
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User savedAdmin = captor.getValue();
        assertThat(savedAdmin.getEmail()).isEqualTo("admin@example.com");
        assertThat(savedAdmin.getPassword()).isEqualTo("encodedPassword");
        assertThat(savedAdmin.getName()).isEqualTo("관리자");
        assertThat(savedAdmin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(savedAdmin.isLocked()).isFalse();
    }

    @Test
    void initialize_adminAlreadyExists_doesNotCreateAdmin() {
        // given
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(true);

        // when
        adminInitializer.initialize();

        // then
        verify(userRepository).existsByEmail("admin@example.com");
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }
}