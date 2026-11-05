package com.otboo.team03.feed.controller;

import com.otboo.team03.feed.dto.request.FeedCreateRequest;
import com.otboo.team03.feed.dto.request.FeedUpdateRequest;
import com.otboo.team03.feed.dto.response.FeedDto;
import com.otboo.team03.feed.service.FeedService;
import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.global.security.OtbooUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/feeds")
public class FeedController {

  private final FeedService feedService;

  @PostMapping
  public ResponseEntity<FeedDto> create(
      @Valid @RequestBody FeedCreateRequest request,
      Authentication authentication
  ) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal()
        instanceof OtbooUserDetails userDetails)) {
      throw new OtbooException(
          ErrorCode.AUTHENTICATION_REQUIRED
      );
    }

    if (!userDetails.getUserDto().id().equals(request.authorId())) {
      throw new OtbooException(ErrorCode.ACCESS_DENIED);
    }

    FeedDto response = feedService.create(request);

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(response);
  }

  @PatchMapping("/{feedId}")
  public ResponseEntity<FeedDto> update(
      @PathVariable UUID feedId,
      @RequestBody FeedUpdateRequest request,
      Authentication authentication
  ) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal()
        instanceof OtbooUserDetails userDetails)) {
      throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    UUID requesterId = userDetails.getUserDto().id();

    FeedDto response = feedService.update(
        feedId,
        requesterId,
        request
    );

    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{feedId}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID feedId,
      Authentication authentication
  ) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal()
        instanceof OtbooUserDetails userDetails)) {
      throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    UUID requesterId = userDetails.getUserDto().id();

    feedService.delete(feedId, requesterId);

    return ResponseEntity.noContent().build();
  }

  /**
   * 인증된 사용자가 피드 한 건을 조회한다.
   *
   * <p>목록 조회와 동일하게 로그인 사용자의 ID를 서비스에 전달한다. 이 ID는 응답의
   * likedByMe 등 조회 사용자에 따라 달라지는 값을 계산하는 데 사용한다.
   * 의상 이미지 URL 변환은 서비스가 호출하는 기존 FeedDtoAssembler에서 수행한다.
   *
   * @param feedId 조회할 피드 ID
   * @param authentication 보안 필터에서 설정한 인증 정보
   * @return 작성자·날씨·연결된 의상 정보를 포함한 피드 응답
   */
  @GetMapping("/{feedId}")
  public ResponseEntity<FeedDto> getFeed(
      @PathVariable UUID feedId,
      Authentication authentication
  ) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof OtbooUserDetails userDetails)) {
      throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    // 요청 파라미터로 사용자 ID를 받지 않고 인증 정보에서 조회 사용자를 결정한다.
    return ResponseEntity.ok(
        feedService.getFeed(feedId, userDetails.getUserDto().id()));
  }

  @GetMapping
  public ResponseEntity<CursorResponse<FeedDto>> getFeedList(
      @RequestParam int limit,
      @RequestParam String sortBy,
      @RequestParam String sortDirection,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam(required = false) String keywordLike,
      @RequestParam(required = false) UUID authorIdEqual,
      @RequestParam(required = false) String skyStatusEqual,
      @RequestParam(required = false) String precipitationTypeEqual,
      Authentication authentication
  ) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal()
        instanceof OtbooUserDetails userDetails)) {
      throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    UUID viewerId = userDetails.getUserDto().id();

    CursorResponse<FeedDto> response = feedService.getFeedList(
        limit,
        sortBy,
        sortDirection,
        cursor,
        idAfter,
        keywordLike,
        authorIdEqual,
        skyStatusEqual,
        precipitationTypeEqual,
        viewerId
    );

    return ResponseEntity.ok(response);
  }
}
