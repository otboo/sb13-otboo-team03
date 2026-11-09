package com.otboo.team03.follow.controller;

import com.otboo.team03.follow.dto.request.FollowCreateRequest;
import com.otboo.team03.follow.dto.response.FollowDto;
import com.otboo.team03.follow.dto.response.UserSummary;
import com.otboo.team03.follow.service.FollowService;
import com.otboo.team03.global.exception.GlobalExceptionHandler;
import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FollowControllerTest {

  @Mock
  private FollowService followService;

  @InjectMocks
  private FollowController followController;

  private MockMvc mockMvc;

  private final UUID followerId = UUID.randomUUID();
  private final UUID followeeId = UUID.randomUUID();
  private final UUID followId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders
        .standaloneSetup(followController)
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  void 팔로우_생성에_성공하면_201과_FollowDto를_반환한다()
      throws Exception {
    // given
    FollowCreateRequest request = new FollowCreateRequest(
        followerId,
        followeeId
    );

    FollowDto response = new FollowDto(
        followId,
        new UserSummary(followeeId, "팔로우 대상", "followee.png"),
        new UserSummary(followerId, "팔로워", "follower.png")
    );

    when(followService.create(followerId, request))
        .thenReturn(response);

    String requestJson = """
            {
                "followerId": "%s",
                "followeeId": "%s"
            }
            """.formatted(followerId, followeeId);

    // when
    var result = mockMvc.perform(
        post("/api/follows")
            .principal(authentication(followerId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
    );

    // then
    result.andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(followId.toString()))
        .andExpect(jsonPath("$.followee.userId")
            .value(followeeId.toString()))
        .andExpect(jsonPath("$.followee.name")
            .value("팔로우 대상"))
        .andExpect(jsonPath("$.followee.profileImageUrl")
            .value("followee.png"))
        .andExpect(jsonPath("$.follower.userId")
            .value(followerId.toString()))
        .andExpect(jsonPath("$.follower.name")
            .value("팔로워"))
        .andExpect(jsonPath("$.follower.profileImageUrl")
            .value("follower.png"));

    verify(followService).create(followerId, request);
  }

  @Test
  void 팔로우_취소에_성공하면_204를_반환한다()
      throws Exception {
    // given
    UUID requesterId = followerId;

    // when
    var result = mockMvc.perform(
        delete("/api/follows/{followId}", followId)
            .principal(authentication(requesterId))
    );

    // then
    result.andExpect(status().isNoContent())
        .andExpect(content().string(""));

    verify(followService).delete(followId, requesterId);
  }

  @Test
  void 미인증_사용자는_팔로우를_생성하거나_취소할_수_없다()
      throws Exception {
    // given
    String requestJson = """
            {
                "followerId": "%s",
                "followeeId": "%s"
            }
            """.formatted(followerId, followeeId);

    // when
    var createResult = mockMvc.perform(
        post("/api/follows")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
    );

    var deleteResult = mockMvc.perform(
        delete("/api/follows/{followId}", followId)
    );

    // then
    createResult.andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.exceptionName")
            .value("AUTHENTICATION_REQUIRED"));

    deleteResult.andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.exceptionName")
            .value("AUTHENTICATION_REQUIRED"));

    verifyNoInteractions(followService);
  }

  private Authentication authentication(UUID userId) {
    UserDto userDto = new UserDto(
        userId,
        Instant.now(),
        "test@example.com",
        "테스트 사용자",
        UserRole.USER,
        false
    );

    OtbooUserDetails userDetails = new OtbooUserDetails(
        userDto,
        "encodedPassword"
    );

    return new UsernamePasswordAuthenticationToken(
        userDetails,
        null,
        userDetails.getAuthorities()
    );
  }
}
