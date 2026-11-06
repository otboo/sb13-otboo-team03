package com.otboo.team03.feed.controller;

import com.otboo.team03.feed.dto.request.CommentCreateRequest;
import com.otboo.team03.feed.dto.request.FeedCreateRequest;
import com.otboo.team03.feed.dto.request.FeedUpdateRequest;
import com.otboo.team03.feed.dto.response.CommentDto;
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

  @PostMapping("/{feedId}/like")
  public ResponseEntity<Void> like(
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

    feedService.like(feedId, requesterId);

    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{feedId}/like")
  public ResponseEntity<Void> unlike(
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

    feedService.unlike(feedId, requesterId);

    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{feedId}/comments")
  public ResponseEntity<CommentDto> createComment(
      @PathVariable UUID feedId,
      @RequestBody CommentCreateRequest request,
      Authentication authentication
  ) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal()
        instanceof OtbooUserDetails userDetails)) {
      throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    UUID requesterId = userDetails.getUserDto().id();

    CommentDto response = feedService.createComment(
        feedId,
        requesterId,
        request
    );

    return ResponseEntity.ok(response);
  }

  @GetMapping("/{feedId}/comments")
  public ResponseEntity<CursorResponse<CommentDto>> getFeedComments(
      @PathVariable UUID feedId,
      @RequestParam int limit,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      Authentication authentication
  ) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal()
        instanceof OtbooUserDetails)) {
      throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    CursorResponse<CommentDto> response =
        feedService.getFeedComments(
            feedId,
            limit,
            cursor,
            idAfter
        );

    return ResponseEntity.ok(response);
  }
}
