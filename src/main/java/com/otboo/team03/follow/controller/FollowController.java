package com.otboo.team03.follow.controller;

import com.otboo.team03.follow.dto.request.FollowCreateRequest;
import com.otboo.team03.follow.dto.response.FollowDto;
import com.otboo.team03.follow.service.FollowService;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.global.security.OtbooUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/follows")
public class FollowController {

  private final FollowService followService;

  @PostMapping
  public ResponseEntity<FollowDto> create(
      @RequestBody FollowCreateRequest request,
      Authentication authentication
  ) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal()
        instanceof OtbooUserDetails userDetails)) {
      throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    UUID requesterId = userDetails.getUserDto().id();

    FollowDto response = followService.create(
        requesterId,
        request
    );

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(response);
  }

  @DeleteMapping("/{followId}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID followId,
      Authentication authentication
  ) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal()
        instanceof OtbooUserDetails userDetails)) {
      throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    UUID requesterId = userDetails.getUserDto().id();

    followService.delete(followId, requesterId);

    return ResponseEntity.noContent().build();
  }
}
