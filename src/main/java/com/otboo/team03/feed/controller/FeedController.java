package com.otboo.team03.feed.controller;

import com.otboo.team03.feed.dto.request.FeedCreateRequest;
import com.otboo.team03.feed.dto.response.FeedDto;
import com.otboo.team03.feed.service.FeedService;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.global.security.OtbooUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
