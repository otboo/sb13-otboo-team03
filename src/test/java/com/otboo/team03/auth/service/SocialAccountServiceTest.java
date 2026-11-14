package com.otboo.team03.auth.service;

import com.otboo.team03.auth.dto.SocialIdentity;
import com.otboo.team03.auth.entity.SocialAccount;
import com.otboo.team03.auth.entity.SocialProvider;
import com.otboo.team03.auth.repository.SocialAccountRepository;
import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.profile.repository.ProfileRepository;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import com.otboo.team03.global.config.CacheNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.cache.Cache;
import org.springframework.security.authentication.LockedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;

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

    @Mock
    private CacheManager cacheManager;

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

        @Test
        @DisplayName("제공자가 없거나 회원 ID가 비어 있으면 소셜 계정 조회 없이 거부한다")
        void resolveUser_invalidIdentity_rejectsBeforeLookup() {
            // given
            SocialIdentity missingProvider = new SocialIdentity(null, GOOGLE_ID, EMAIL, NAME, true);
            SocialIdentity blankProviderUserId = new SocialIdentity(SocialProvider.GOOGLE, " ", EMAIL, NAME, true);

            // when & then
            assertThatThrownBy(() -> socialAccountService.resolveUser(missingProvider))
                    .isInstanceOf(BadCredentialsException.class);
            assertThatThrownBy(() -> socialAccountService.resolveUser(blankProviderUserId))
                    .isInstanceOf(BadCredentialsException.class);

            then(socialAccountRepository).shouldHaveNoInteractions();
            then(userRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("기존 사용자에게 소셜 계정이 이미 연결되어 있으면 다른 Google 계정을 자동 연동하지 않는다")
        void resolveUser_alreadyLinkedUser_rejectsAnotherGoogleAccount() {
            // given
            User existingUser = new User(EMAIL, "encodedPassword", NAME);
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(existingUser));
            given(socialAccountRepository.existsByUser(existingUser)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> socialAccountService.resolveUser(GOOGLE_IDENTITY))
                    .isInstanceOf(BadCredentialsException.class);

            then(socialAccountRepository).should(never()).save(any(SocialAccount.class));
            then(userRepository).should(never()).save(any(User.class));
        }

        @Test
        @DisplayName("이미 연결된 소셜 계정이라도 사용자가 잠겨 있으면 로그인을 거부한다")
        void resolveUser_lockedLinkedUser_rejectsLogin() {
            // given
            User lockedUser = new User(EMAIL, null, NAME);
            lockedUser.changeLocked(true);
            SocialAccount account = new SocialAccount(lockedUser, SocialProvider.GOOGLE, GOOGLE_ID);
            given(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, GOOGLE_ID))
                    .willReturn(Optional.of(account));

            // when & then
            assertThatThrownBy(() -> socialAccountService.resolveUser(GOOGLE_IDENTITY))
                    .isInstanceOf(LockedException.class);

            then(userRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("닉네임이 없는 Kakao 사용자는 기본 이름과 가상 이메일로 가입하고 사용자 목록 캐시를 비운다")
        void resolveUser_kakaoWithoutNickname_createsUserAndClearsCache() {
            // given
            String kakaoId = "12345";
            String virtualEmail = "카카오 사용자_12345@kakao.com";
            SocialIdentity identity = new SocialIdentity(SocialProvider.KAKAO, kakaoId, null, null, false);
            Cache userListCache = mock(Cache.class);

            given(userRepository.findByEmail(virtualEmail)).willReturn(Optional.empty());
            given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
            given(cacheManager.getCache(CacheNames.USER_LIST)).willReturn(userListCache);

            // when
            User user = socialAccountService.resolveUser(identity);

            // then
            assertThat(user.getName()).isEqualTo("카카오 사용자");
            assertThat(user.getEmail()).isEqualTo(virtualEmail);
            assertThat(user.getPassword()).isNull();

            ArgumentCaptor<SocialAccount> captor = ArgumentCaptor.forClass(SocialAccount.class);
            then(socialAccountRepository).should().save(captor.capture());
            assertThat(captor.getValue().getUser()).isSameAs(user);
            assertThat(captor.getValue().getProvider()).isEqualTo(SocialProvider.KAKAO);
            assertThat(captor.getValue().getProviderUserId()).isEqualTo(kakaoId);
            then(userListCache).should().clear();
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
