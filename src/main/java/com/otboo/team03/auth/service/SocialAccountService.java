package com.otboo.team03.auth.service;

import com.otboo.team03.auth.dto.SocialIdentity;
import com.otboo.team03.auth.entity.SocialAccount;
import com.otboo.team03.auth.entity.SocialProvider;
import com.otboo.team03.auth.repository.SocialAccountRepository;
import com.otboo.team03.global.config.CacheNames;
import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.profile.repository.ProfileRepository;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SocialAccountService {

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final CacheManager cacheManager;

    // 제공자와 회원 ID로 기존 계정을 찾고, 연결 정보가 없으면 최초 로그인으로 처리한다.
    @Transactional
    public User resolveUser(SocialIdentity identity) {
        if (identity.provider() == null || !StringUtils.hasText(identity.providerUserId())) {
            throw new BadCredentialsException("Invalid social account identity");
        }

        return socialAccountRepository.findByProviderAndProviderUserId(identity.provider(), identity.providerUserId())
                .map(SocialAccount::getUser)
                .map(this::requireUnlocked)
                .orElseGet(() -> resolveFirstLogin(identity));
    }

    // Google은 검증된 이메일을, Kakao는 가상 이메일을 사용해 기존 계정 연결 또는 신규 가입을 처리한다.
    private User resolveFirstLogin(SocialIdentity identity) {
        boolean google = identity.provider() == SocialProvider.GOOGLE;
        if (google && (!identity.emailVerified() || !StringUtils.hasText(identity.email()))) {
            throw new BadCredentialsException("Google email is not verified");
        }

        String defaultName = google ? "Google 사용자" : "카카오 사용자";
        String name = StringUtils.hasText(identity.name()) ? identity.name() : defaultName;
        String email = google ? identity.email() : "%s_%s@kakao.com".formatted(name, identity.providerUserId());

        return userRepository.findByEmail(email)
                .map(user -> linkExistingUser(user, identity))
                .orElseGet(() -> createUser(email, name, identity));
    }

    // 검증된 Google 이메일이 일치하고 아직 소셜 계정이 연결되지 않은 경우에만 기존 계정에 연결한다.
    // Kakao 가상 이메일이 기존 이메일과 겹치면 연결하지 않는다.
    private User linkExistingUser(User user, SocialIdentity identity) {
        if (identity.provider() != SocialProvider.GOOGLE || socialAccountRepository.existsByUser(user)) {
            throw new BadCredentialsException("Email already in use");
        }

        requireUnlocked(user);
        saveSocialAccount(user, identity);
        return user;
    }

    // 비밀번호 없이 사용자와 기본 프로필을 생성하고, 소셜 계정을 연결한 뒤 사용자 목록 캐시를 비운다.
    private User createUser(String email, String name, SocialIdentity identity) {
        User user = userRepository.save(new User(email, null, name));
        profileRepository.save(new Profile(user));
        saveSocialAccount(user, identity);

        Cache userListCache = cacheManager.getCache(CacheNames.USER_LIST);
        if (userListCache != null) {
            userListCache.clear();
        }

        return user;
    }

    // OTBOO 사용자와 소셜 제공자의 회원 ID 간 연결 정보를 저장한다.
    private void saveSocialAccount(User user, SocialIdentity identity) {
        socialAccountRepository.save(new SocialAccount(user, identity.provider(), identity.providerUserId()));
    }

    // 잠긴 계정은 소셜 로그인과 자동 연동을 허용하지 않는다.
    private User requireUnlocked(User user) {
        if (user.isLocked()) {
            throw new LockedException("Account is locked");
        }

        return user;
    }
}