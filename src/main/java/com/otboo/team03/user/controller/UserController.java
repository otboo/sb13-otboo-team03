package com.otboo.team03.user.controller;

import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.user.dto.request.UserCreateRequest;
import com.otboo.team03.user.dto.request.UserRoleUpdateRequest;
import com.otboo.team03.user.dto.request.UserListRequest;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<UserDto> updateRole(@PathVariable UUID userId,
                                              @Valid @RequestBody UserRoleUpdateRequest request) {
        UserDto response = userService.updateRole(userId, request.role());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<CursorResponse<UserDto>> getUsers(@Valid @ModelAttribute UserListRequest request) {
        return ResponseEntity.ok(userService.getUsers(request));
    }
}
