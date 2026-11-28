package com.otboo.team03.profile.controller;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.ErrorResponse;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.profile.dto.ProfileDto;
import com.otboo.team03.profile.dto.ProfileUpdateRequest;
import com.otboo.team03.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users/{userId}/profiles")
@RequiredArgsConstructor
public class ProfileController {

  private final ProfileService profileService;

  // 프로필 단건 조회(GET)
  @Operation(
      responses = {
          @ApiResponse(responseCode = "200", description = "프로필 조회 성공",
              content = @Content(schema = @Schema(implementation = ProfileDto.class))),
          @ApiResponse(responseCode = "400", description = "프로필 조회 실패",
              content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
      })
  @GetMapping
  public ResponseEntity<ProfileDto> getProfile(
      @PathVariable UUID userId,
      Authentication authentication // 1. 시큐리티 인증 객체를 파라미터로 주입받음
  ) {
    // 1. 로그인 여부 확인: 미인증 요청이면 401 UNAUTHORIZED 예외 발생
    if (authentication == null || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof OtbooUserDetails)) {
      throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    ProfileDto response = profileService.getProfile(userId);
    return ResponseEntity.ok(response);
  }

  // 프로필 수정(PATCH)
  @Operation(
      responses = {
          @ApiResponse(responseCode = "200", description = "프로필 업데이트 성공",
              content = @Content(schema = @Schema(implementation = ProfileDto.class))),
          @ApiResponse(responseCode = "400", description = "프로필 업데이트 실패",
              content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
      })
  @PatchMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ProfileDto> updateProfile(
      @PathVariable UUID userId,
      @RequestPart(value = "request", required = false) @Valid ProfileUpdateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image,
      Authentication authentication
  ) {
    // 1. 본인 확인 (미인증 401, 타인 403 차단)
    if (authentication == null || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof OtbooUserDetails userDetails)) {
      throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    if (!userDetails.getUserDto().id().equals(userId)){
      throw new OtbooException(ErrorCode.ACCESS_DENIED);
    }
    // 2. 서비스 호출 (profileService.updateProfile)
    ProfileDto response = profileService.updateProfile(userId, request, image);
    // 3. 200 OK와 함께 수정된 ProfileDto 반환
    return ResponseEntity.ok(response);
  }

}
