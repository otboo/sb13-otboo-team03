package com.otboo.team03.user.init;

import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.profile.repository.ProfileRepository;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.entity.UserRole;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminInitializer adminInitializer;

    @BeforeEach
    void setUp() {
        AdminProperties properties = new AdminProperties("admin@example.com", "adminPassword123!", "관리자");
        adminInitializer = new AdminInitializer(userRepository, profileRepository, passwordEncoder, properties);
    }

    @Test
    void initialize_adminNotExists_createsAdmin() {
        // given
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);
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
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(true);

        // when
        adminInitializer.initialize();

        // then
        verify(userRepository).existsByRole(UserRole.ADMIN);
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(profileRepository);
    }

    @Test
    void run_onApplicationStartup_initializesAdmin() throws Exception {
        // given
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode("adminPassword123!")).thenReturn("encodedPassword");

        // when
        adminInitializer.run(new DefaultApplicationArguments(new String[0]));

        // then
        verify(userRepository).save(any(User.class));
        verify(profileRepository).save(any(Profile.class));
    }

    @Test
    void initialize_adminNotExists_createsProfile() {
        // given
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode("adminPassword123!")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        adminInitializer.initialize();

        // then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<Profile> profileCaptor = ArgumentCaptor.forClass(Profile.class);

        verify(userRepository).save(userCaptor.capture());
        verify(profileRepository).save(profileCaptor.capture());

        User savedAdmin = userCaptor.getValue();
        Profile savedProfile = profileCaptor.getValue();

        assertThat(savedProfile.getUser()).isSameAs(savedAdmin);
        assertThat(savedAdmin.getRole()).isEqualTo(UserRole.ADMIN);
    }

}