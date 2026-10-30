package com.otboo.team03.user.controller;

import com.otboo.team03.user.dto.request.UserCreateRequest;
import com.otboo.team03.user.dto.request.UserRoleUpdateRequest;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody UserCreateRequest request) {
        UserDto response = userService.createUser(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PatchMapping("/{userId}/role")
    public ResponseEntity<Void> updateRole(@PathVariable UUID userId,
                                           @Valid @RequestBody UserRoleUpdateRequest request) {
        userService.updateRole(userId, request.role());
        return ResponseEntity.noContent().build();
    }
}
