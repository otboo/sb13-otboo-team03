package com.otboo.team03.user.service;

import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.user.dto.request.UserCreateRequest;
import com.otboo.team03.user.dto.request.UserListRequest;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.entity.UserRole;
import com.otboo.team03.user.exception.DuplicateEmailException;
import com.otboo.team03.user.exception.LastAdminException;
import com.otboo.team03.user.exception.UserNotFoundException;
import com.otboo.team03.user.repository.RedisTemporaryPasswordRepository;
import com.otboo.team03.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtRegistry jwtRegistry;
    private final RedisTemporaryPasswordRepository temporaryPasswordRepository;

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
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto updateRole(UUID userId, UserRole role) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (user.getRole() == role) {
            return UserDto.from(user);
        }

        if (user.getRole() == UserRole.ADMIN && role == UserRole.USER && userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new LastAdminException();
        }

        user.changeRole(role);

        // db 트랜잭션이 커밋된 이후에 Redis 세션 무효화되도록
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                jwtRegistry.invalidateJwtInformationByUserId(userId);
            }
        });

        return UserDto.from(user);
    }


    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public CursorResponse<UserDto> getUsers(UserListRequest request) {
        List<User> users = userRepository.findUsersByCursor(request);
        boolean hasNext = users.size() > request.limit();

        List<User> page = users.stream()
                .limit(request.limit())
                .toList();

        List<UserDto> data = page.stream()
                .map(UserDto::from)
                .toList();

        User lastUser = page.isEmpty() ? null : page.get(page.size() - 1);

        String nextCursor = hasNext
                ? "createdAt".equals(request.sortBy())
                  ? lastUser.getCreatedAt().toString()
                  : lastUser.getEmail()
                : null;

        UUID nextIdAfter = hasNext ? lastUser.getId() : null;

        long totalCount = userRepository.countUsers(request);

        return new CursorResponse<>(
                data,
                nextCursor,
                nextIdAfter,
                hasNext,
                totalCount,
                request.sortBy(),
                request.sortDirection()
        );
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
