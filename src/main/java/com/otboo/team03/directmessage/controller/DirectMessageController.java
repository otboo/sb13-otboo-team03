package com.otboo.team03.directmessage.controller;

import com.otboo.team03.directmessage.dto.request.DirectMessageCreateRequest;
import com.otboo.team03.directmessage.dto.response.DirectMessageDto;
import com.otboo.team03.directmessage.service.DirectMessageService;
import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.global.security.OtbooUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/direct-messages")
public class DirectMessageController {

    private final DirectMessageService directMessageService;

    @GetMapping
    public ResponseEntity<CursorResponse<DirectMessageDto>> getMessages(
            @RequestParam UUID userId,
            @RequestParam int limit,
            @RequestParam(required = false) Instant cursor,
            @RequestParam(required = false) UUID idAfter,
            Authentication authentication
    ) {
        UUID requesterId = getAuthenticatedUserId(authentication);

        return ResponseEntity.ok(directMessageService.getMessages(
                requesterId,
                userId,
                cursor,
                idAfter,
                limit
        ));
    }

    @PostMapping
    public ResponseEntity<DirectMessageDto> send(
            @Valid @RequestBody DirectMessageCreateRequest request,
            Authentication authentication
    ) {
        UUID senderId = getAuthenticatedUserId(authentication);
        DirectMessageDto response = directMessageService.send(
                senderId,
                request.receiverId(),
                request.content()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private UUID getAuthenticatedUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof OtbooUserDetails userDetails)) {
            throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        return userDetails.getUserDto().id();
    }
}
