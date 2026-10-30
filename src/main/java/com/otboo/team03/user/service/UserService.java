package com.otboo.team03.user.service;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.user.dto.request.UserCreateRequest;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.entity.UserRole;
import com.otboo.team03.user.exception.DuplicateEmailException;
import com.otboo.team03.user.exception.LastAdminException;
import com.otboo.team03.user.exception.UserNotFoundException;
import com.otboo.team03.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
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
    @PreAuthorize("hasRole('ADMIN')")
    public void updateRole(UUID userId, UserRole role) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (user.getRole() == role) {
            return;
        }

        if (user.getRole() == UserRole.ADMIN && role == UserRole.USER && userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new LastAdminException();
        }

        user.changeRole(role);
        jwtRegistry.invalidateJwtInformationByUserId(userId);
    }

}
