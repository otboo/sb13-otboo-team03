package com.otboo.team03.user.service;

import com.otboo.team03.global.config.CacheNames;
import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.profile.repository.ProfileRepository;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final long ADMIN_ADVISORY_LOCK_ID = 720072L;

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtRegistry jwtRegistry;
    private final RedisTemporaryPasswordRepository temporaryPasswordRepository;

    @CacheEvict(cacheNames = CacheNames.USER_LIST, allEntries = true)
    @Transactional
    public UserDto createUser(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException();
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        User user = new User(request.email(), encodedPassword, request.name());
        User savedUser = userRepository.save(user);

        Profile profile = new Profile(savedUser);
        profileRepository.save(profile);

        return UserDto.from(savedUser);
    }

    @CacheEvict(cacheNames = CacheNames.USER_LIST, allEntries = true)
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and #userId != authentication.principal.userDto.id")
    public UserDto updateRole(UUID userId, UserRole role) {
        // 관리자 권한 및 잠금 변경을 직렬화
        userRepository.acquireAdminLock(ADMIN_ADVISORY_LOCK_ID);

        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (user.getRole() == role) {
            return UserDto.from(user);
        }

        if (user.getRole() == UserRole.ADMIN && role == UserRole.USER) {
            if (userRepository.countByRole(UserRole.ADMIN) <= 1) {
                throw new LastAdminException();
            }

            // 잠금 해제된 마지막 ADMIN도 보호
            if (!user.isLocked() && userRepository.countByRoleAndLockedFalse(UserRole.ADMIN) <= 1) {
                throw new LastAdminException();
            }
        }

        UserRole previousRole = user.getRole();
        user.changeRole(role);

        UUID actorId = getCurrentUserId();

        // DB 트랜잭션 커밋 이후 Redis 세션 무효화
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("User role changed: actorId={}, targetUserId={}, previousRole={}, newRole={}",
                        actorId, userId, previousRole, role);

                try {
                    jwtRegistry.invalidateJwtInformationByUserId(userId);
                } catch (RuntimeException e) {
                    log.error("Failed to invalidate JWT session after role change: userId={}", userId, e);
                }
            }
        });

        return UserDto.from(user);
    }


    @Cacheable(
            cacheNames = CacheNames.USER_LIST,
            condition = "#p0.isDefaultFirstPage()"
    )
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

        // DB 트랜잭션이 커밋된 이후 Redis 세션 무효화
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    temporaryPasswordRepository.delete(user.getEmail());
                } catch (RuntimeException e) {
                    log.error("Failed to delete temporary password after password change: userId={}", userId, e);
                }

                try {
                    jwtRegistry.invalidateJwtInformationByUserId(userId);
                } catch (RuntimeException e) {
                    log.error("Failed to invalidate JWT session after password change: userId={}", userId, e);
                }
            }
        });
    }

    @CacheEvict(cacheNames = CacheNames.USER_LIST, allEntries = true)
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and #userId != authentication.principal.userDto.id")
    public UserDto updateLock(UUID userId, boolean locked) {
        // 관리자 권한 및 잠금 변경에 동일한 락 사용
        userRepository.acquireAdminLock(ADMIN_ADVISORY_LOCK_ID);

        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (user.isLocked() == locked) {
            return UserDto.from(user);
        }

        if (locked && user.getRole() == UserRole.ADMIN
                && userRepository.countByRoleAndLockedFalse(UserRole.ADMIN) <= 1) {
            throw new LastAdminException();
        }

        boolean previousLocked = user.isLocked();
        user.changeLocked(locked);

        UUID actorId = getCurrentUserId();

        if (locked) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("User lock changed: actorId={}, targetUserId={}, previousLocked={}, newLocked={}",
                            actorId, userId, previousLocked, locked);

                    try {
                        jwtRegistry.invalidateJwtInformationByUserId(userId);
                    } catch (RuntimeException e) {
                        log.error("Failed to invalidate JWT session after account lock: userId={}", userId, e);
                    }
                }
            });
        }

        return UserDto.from(user);
    }

    private UUID getCurrentUserId() {
        OtbooUserDetails principal = (OtbooUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        return principal.getUserDto().id();
    }
}
