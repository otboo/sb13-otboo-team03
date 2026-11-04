package com.otboo.team03.user.service;

import com.otboo.team03.user.exception.UserNotFoundException;
import com.otboo.team03.user.repository.RedisTemporaryPasswordRepository;
import com.otboo.team03.user.repository.UserRepository;
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

    public void resetPassword(String email) {
        userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);

        String temporaryPassword = generateTemporaryPassword();
        String encodedPassword = passwordEncoder.encode(temporaryPassword);

        temporaryPasswordRepository.save(email, encodedPassword);

        try {
            mailService.sendTemporaryPassword(email, temporaryPassword);
        } catch (RuntimeException exception) {
            temporaryPasswordRepository.delete(email);
            throw exception;
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
