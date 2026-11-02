package com.otboo.team03.user.service;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.user.dto.request.UserCreateRequest;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.exception.DuplicateEmailException;
import com.otboo.team03.user.exception.UserNotFoundException;
import com.otboo.team03.user.repository.RedisTemporaryPasswordRepository;
import com.otboo.team03.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemporaryPasswordRepository temporaryPasswordRepository;
    private final JwtRegistry jwtRegistry;

    @Transactional
    public UserDto createUser(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException();
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        User user = new User(request.email(), encodedPassword, request.name());
        User savedUser = userRepository.save(user);

        return UserDto.from(savedUser);
    }

    @Transactional
    public void changePassword(UUID userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        String encodedPassword = passwordEncoder.encode(newPassword);
        user.changePassword(encodedPassword);

        // db 트랜잭션이 커밋된 이후에 Redis 세션 무효화되도록
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                temporaryPasswordRepository.delete(user.getEmail());
                jwtRegistry.invalidateJwtInformationByUserId(userId);
            }
        });
    }
}
