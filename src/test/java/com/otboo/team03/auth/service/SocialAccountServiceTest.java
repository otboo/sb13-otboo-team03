package com.otboo.team03.auth.service;

import com.otboo.team03.auth.dto.SocialIdentity;
import com.otboo.team03.auth.entity.SocialAccount;
import com.otboo.team03.auth.entity.SocialProvider;
import com.otboo.team03.auth.repository.SocialAccountRepository;
import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.profile.repository.ProfileRepository;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SocialAccountServiceTest {

    private static final String GOOGLE_ID = "google-user-123";
    private static final String EMAIL = "user@example.com";
    private static final String NAME = "구글 사용자";
    private static final SocialIdentity GOOGLE_IDENTITY = new SocialIdentity(SocialProvider.GOOGLE, GOOGLE_ID, EMAIL, NAME, true);

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private SocialAccountService socialAccountService;

    @Nested
    @DisplayName("소셜 로그인 계정 결정")
    class ResolveUserTest {

        @Test
        @DisplayName("신규 소셜 가입 시 비밀번호 없이 사용자와 기본 프로필, 소셜 계정 정보를 생성한다")
        void resolveUser_firstLogin_createsUserProfileAndSocialAccount() {
            // given
            given(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, GOOGLE_ID))
                    .willReturn(Optional.empty());
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());
            given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            User user = socialAccountService.resolveUser(GOOGLE_IDENTITY);

            // then
            assertThat(user.getEmail()).isEqualTo(EMAIL);
            assertThat(user.getName()).isEqualTo(NAME);
            assertThat(user.getPassword()).isNull();
            then(userRepository).should().save(user);

            ArgumentCaptor<Profile> profileCaptor = ArgumentCaptor.forClass(Profile.class);
            then(profileRepository).should().save(profileCaptor.capture());
            assertThat(profileCaptor.getValue().getUser()).isSameAs(user);
            assertThat(profileCaptor.getValue().getLocationNames()).isEmpty();
            assertSocialAccountSavedFor(user);
        }

        @Test
        @DisplayName("같은 소셜 계정으로 다시 로그인하면 제공자와 회원 ID로 기존 사용자를 찾는다")
        void resolveUser_existingSocialAccount_returnsLinkedUser() {
            // given
            User linkedUser = new User(EMAIL, null, "수정한 이름");
            SocialAccount socialAccount = new SocialAccount(linkedUser, SocialProvider.GOOGLE, GOOGLE_ID);
            given(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, GOOGLE_ID))
                    .willReturn(Optional.of(socialAccount));
            SocialIdentity updatedIdentity = new SocialIdentity(SocialProvider.GOOGLE, GOOGLE_ID, "changed@example.com", NAME, true);

            // when
            User user = socialAccountService.resolveUser(updatedIdentity);

            // then
            assertThat(user).isSameAs(linkedUser);
            assertThat(user.getEmail()).isEqualTo(EMAIL);
            assertThat(user.getName()).isEqualTo("수정한 이름");
            then(userRepository).shouldHaveNoInteractions();
            then(profileRepository).shouldHaveNoInteractions();
            then(socialAccountRepository).should(never()).save(any(SocialAccount.class));
        }

        @Test
        @DisplayName("Google의 검증된 이메일이 기존 일반 계정과 같으면 기존 계정에 연결한다")
        void resolveUser_verifiedGoogleEmail_linksExistingUser() {
            // given
            User existingUser = new User(EMAIL, "encodedPassword", "기존 이름");
            given(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, GOOGLE_ID))
                    .willReturn(Optional.empty());
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(existingUser));

            // when
            User user = socialAccountService.resolveUser(GOOGLE_IDENTITY);

            // then
            assertThat(user).isSameAs(existingUser);
            assertThat(user.getPassword()).isEqualTo("encodedPassword");
            assertThat(user.getName()).isEqualTo("기존 이름");
            then(userRepository).should(never()).save(any(User.class));
            then(profileRepository).shouldHaveNoInteractions();
            assertSocialAccountSavedFor(existingUser);
        }

        @Test
        @DisplayName("Google 이메일이 검증되지 않았으면 가입하거나 연결하지 않는다")
        void resolveUser_unverifiedGoogleEmail_rejectsLogin() {
            // given
            SocialIdentity unverifiedIdentity = new SocialIdentity(SocialProvider.GOOGLE, GOOGLE_ID, EMAIL, NAME, false);

            // when & then
            assertThatThrownBy(() -> socialAccountService.resolveUser(unverifiedIdentity))
                    .isInstanceOf(BadCredentialsException.class);
            then(userRepository).should(never()).save(any(User.class));
            then(profileRepository).shouldHaveNoInteractions();
            then(socialAccountRepository).should(never()).save(any(SocialAccount.class));
        }
    }

    private void assertSocialAccountSavedFor(User user) {
        ArgumentCaptor<SocialAccount> socialAccountCaptor = ArgumentCaptor.forClass(SocialAccount.class);
        then(socialAccountRepository).should().save(socialAccountCaptor.capture());
        SocialAccount socialAccount = socialAccountCaptor.getValue();
        assertThat(socialAccount.getUser()).isSameAs(user);
        assertThat(socialAccount.getProvider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(socialAccount.getProviderUserId()).isEqualTo(GOOGLE_ID);
    }
}
