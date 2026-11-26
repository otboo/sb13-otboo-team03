package com.otboo.team03.user.service;

import com.otboo.team03.user.exception.PasswordResetRateLimitException;
import com.otboo.team03.user.exception.UserNotFoundException;
import com.otboo.team03.user.repository.RedisPasswordResetRateLimitRepository;
import com.otboo.team03.user.repository.RedisTemporaryPasswordRepository;
import com.otboo.team03.user.repository.UserRepository;
import com.otboo.team03.auth.entity.SocialProvider;
import com.otboo.team03.auth.repository.SocialAccountRepository;
import com.otboo.team03.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String PASSWORD_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
    private static final int PASSWORD_LENGTH = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RedisTemporaryPasswordRepository temporaryPasswordRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final RedisPasswordResetRateLimitRepository rateLimitRepository;
    private final SocialAccountRepository socialAccountRepository;

    public void resetPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);

        // 카카오 계정의 가상 이메일에는 임시 비밀번호를 발급하지 않는다.
        if (socialAccountRepository.existsByUserIdAndProvider(user.getId(), SocialProvider.KAKAO)) {
            return;
        }

        String token = rateLimitRepository.tryAcquire(email)
                .orElseThrow(PasswordResetRateLimitException::new);

        try {
            String temporaryPassword = generateTemporaryPassword();
            String encodedPassword = passwordEncoder.encode(temporaryPassword);

            temporaryPasswordRepository.save(email, encodedPassword);

            try {
                mailService.sendTemporaryPassword(email, temporaryPassword);
            } catch (RuntimeException e) {
                try {
                    temporaryPasswordRepository.delete(email);
                } catch (RuntimeException cleanupException) {
                    e.addSuppressed(cleanupException);
                }
                throw e;
            }
        } catch (RuntimeException e) {
            try {
                rateLimitRepository.release(email, token);
            } catch (RuntimeException cleanupException) {
                e.addSuppressed(cleanupException);
            }
            throw e;
        }
    }

    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);

        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            int index = SECURE_RANDOM.nextInt(PASSWORD_CHARACTERS.length());
            password.append(PASSWORD_CHARACTERS.charAt(index));
        }

        return password.toString();
    }
}
