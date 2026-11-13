package com.otboo.team03.follow.controller;

import com.otboo.team03.follow.dto.request.FollowCreateRequest;
import com.otboo.team03.follow.dto.response.FollowDto;
import com.otboo.team03.follow.dto.response.UserSummary;
import com.otboo.team03.follow.service.FollowService;
import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.GlobalExceptionHandler;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.UserRole;
import java.util.List;
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

import static org.hamcrest.Matchers.nullValue;
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

  @Test
  void 다른_사용자_명의로_팔로우를_생성하면_403을_반환한다()
      throws Exception {
    // given
    UUID otherUserId = UUID.randomUUID();

    FollowCreateRequest request = new FollowCreateRequest(
        otherUserId,
        followeeId
    );

    when(followService.create(followerId, request))
        .thenThrow(new OtbooException(ErrorCode.ACCESS_DENIED));

    String requestJson = """
        {
            "followerId": "%s",
            "followeeId": "%s"
        }
        """.formatted(otherUserId, followeeId);

    // when
    var result = mockMvc.perform(
        post("/api/follows")
            .principal(authentication(followerId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
    );

    // then
    result.andExpect(status().isForbidden())
        .andExpect(jsonPath("$.exceptionName")
            .value("ACCESS_DENIED"));

    verify(followService).create(followerId, request);
  }

  @Test
  void 중복_팔로우_요청이면_400을_반환한다()
      throws Exception {
    // given
    FollowCreateRequest request = new FollowCreateRequest(
        followerId,
        followeeId
    );

    when(followService.create(followerId, request))
        .thenThrow(
            new OtbooException(ErrorCode.INVALID_INPUT_VALUE)
        );

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
    result.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    verify(followService).create(followerId, request);
  }

  @Test
  void 존재하지_않는_사용자를_팔로우하면_404를_반환한다()
      throws Exception {
    // given
    FollowCreateRequest request = new FollowCreateRequest(
        followerId,
        followeeId
    );

    when(followService.create(followerId, request))
        .thenThrow(
            new OtbooException(ErrorCode.NOT_FOUND_USER)
        );

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
    result.andExpect(status().isNotFound())
        .andExpect(jsonPath("$.exceptionName")
            .value("NOT_FOUND_USER"));

    verify(followService).create(followerId, request);
  }

  @Test
  void 타인의_팔로우를_취소하면_403을_반환한다()
      throws Exception {
    // given
    doThrow(new OtbooException(ErrorCode.ACCESS_DENIED))
        .when(followService)
        .delete(followId, followerId);

    // when
    var result = mockMvc.perform(
        delete("/api/follows/{followId}", followId)
            .principal(authentication(followerId))
    );

    // then
    result.andExpect(status().isForbidden())
        .andExpect(jsonPath("$.exceptionName")
            .value("ACCESS_DENIED"));

    verify(followService).delete(followId, followerId);
  }

  @Test
  void 존재하지_않는_팔로우를_취소하면_404를_반환한다()
      throws Exception {
    // given
    doThrow(new OtbooException(ErrorCode.NOT_FOUND_RESOURCE))
        .when(followService)
        .delete(followId, followerId);

    // when
    var result = mockMvc.perform(
        delete("/api/follows/{followId}", followId)
            .principal(authentication(followerId))
    );

    // then
    result.andExpect(status().isNotFound())
        .andExpect(jsonPath("$.exceptionName")
            .value("NOT_FOUND_RESOURCE"));

    verify(followService).delete(followId, followerId);
  }

  @Test
  void 팔로우_ID_형식이_잘못되면_400을_반환한다()
      throws Exception {
    // given
    String invalidFollowId = "invalid-uuid";

    // when
    var result = mockMvc.perform(
        delete("/api/follows/{followId}", invalidFollowId)
            .principal(authentication(followerId))
    );

    // then
    result.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    verifyNoInteractions(followService);
  }

  @Test
  void 인증되지_않은_Authentication_객체는_팔로우_생성과_취소를_거부한다()
      throws Exception {
    // given
    Authentication unauthenticated =
        new UsernamePasswordAuthenticationToken(
            authentication(followerId).getPrincipal(),
            null
        );

    String requestJson = """
      {
          "followerId": "%s",
          "followeeId": "%s"
      }
      """.formatted(followerId, followeeId);

    // when
    var createResult = mockMvc.perform(
        post("/api/follows")
            .principal(unauthenticated)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
    );

    var deleteResult = mockMvc.perform(
        delete("/api/follows/{followId}", followId)
            .principal(unauthenticated)
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

  @Test
  void 잘못된_Principal_타입은_팔로우_생성과_취소를_거부한다()
      throws Exception {
    // given
    Authentication invalidPrincipal =
        new UsernamePasswordAuthenticationToken(
            "invalid-principal",
            null,
            authentication(followerId).getAuthorities()
        );

    String requestJson = """
      {
          "followerId": "%s",
          "followeeId": "%s"
      }
      """.formatted(followerId, followeeId);

    // when
    var createResult = mockMvc.perform(
        post("/api/follows")
            .principal(invalidPrincipal)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
    );

    var deleteResult = mockMvc.perform(
        delete("/api/follows/{followId}", followId)
            .principal(invalidPrincipal)
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

  @Test
  void 팔로워_목록_조회에_성공하면_200과_커서_응답을_반환한다()
      throws Exception {
    // given
    UUID firstFollowId = UUID.randomUUID();
    UUID lastFollowId = UUID.randomUUID();
    UUID firstFollowerId = UUID.randomUUID();
    UUID lastFollowerId = UUID.randomUUID();
    String nextCursor = "2026-11-09T01:00:00Z";

    FollowDto firstDto = new FollowDto(
        firstFollowId,
        new UserSummary(followeeId, "팔로우 대상", null),
        new UserSummary(firstFollowerId, "김민수", "/images/minsu.png")
    );

    FollowDto secondDto = new FollowDto(
        lastFollowId,
        new UserSummary(followeeId, "팔로우 대상", null),
        new UserSummary(lastFollowerId, "이민지", "/images/minji.png")
    );

    CursorResponse<FollowDto> response = new CursorResponse<>(
        List.of(firstDto, secondDto),
        nextCursor,
        lastFollowId,
        true,
        5L,
        "createdAt",
        "DESCENDING"
    );

    when(followService.getFollowers(
        followeeId, 2, null, null, "민"
    )).thenReturn(response);

    // when
    var result = mockMvc.perform(
        get("/api/follows/followers")
            .principal(authentication(followerId))
            .param("followeeId", followeeId.toString())
            .param("limit", "2")
            .param("nameLike", "민")
    );

    // then
    result.andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id")
            .value(firstFollowId.toString()))
        .andExpect(jsonPath("$.data[0].followee.userId")
            .value(followeeId.toString()))
        .andExpect(jsonPath("$.data[0].follower.userId")
            .value(firstFollowerId.toString()))
        .andExpect(jsonPath("$.data[0].follower.name")
            .value("김민수"))
        .andExpect(jsonPath("$.data[0].follower.profileImageUrl")
            .value("/images/minsu.png"))
        .andExpect(jsonPath("$.data[1].follower.name")
            .value("이민지"))
        .andExpect(jsonPath("$.nextCursor").value(nextCursor))
        .andExpect(jsonPath("$.nextIdAfter")
            .value(lastFollowId.toString()))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.totalCount").value(5))
        .andExpect(jsonPath("$.sortBy").value("createdAt"))
        .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));

    verify(followService).getFollowers(
        followeeId, 2, null, null, "민"
    );
  }

  @Test
  void 팔로워_조회에서_커서와_검색어를_서비스에_전달한다()
      throws Exception {
    // given
    UUID idAfter = UUID.randomUUID();
    String cursor = "2026-11-09T01:00:00Z";

    CursorResponse<FollowDto> response = new CursorResponse<>(
        List.of(),
        null,
        null,
        false,
        3L,
        "createdAt",
        "DESCENDING"
    );

    when(followService.getFollowers(
        followeeId, 10, cursor, idAfter, "김"
    )).thenReturn(response);

    // when
    var result = mockMvc.perform(
        get("/api/follows/followers")
            .principal(authentication(followerId))
            .param("followeeId", followeeId.toString())
            .param("limit", "10")
            .param("cursor", cursor)
            .param("idAfter", idAfter.toString())
            .param("nameLike", "김")
    );

    // then
    result.andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.nextCursor").value(nullValue()))
        .andExpect(jsonPath("$.nextIdAfter").value(nullValue()))
        .andExpect(jsonPath("$.totalCount").value(3));

    verify(followService).getFollowers(
        followeeId, 10, cursor, idAfter, "김"
    );
  }

  @Test
  void 팔로워_조회에서_선택_파라미터를_생략할_수_있다()
      throws Exception {
    // given
    CursorResponse<FollowDto> response = new CursorResponse<>(
        List.of(),
        null,
        null,
        false,
        0L,
        "createdAt",
        "DESCENDING"
    );

    when(followService.getFollowers(
        followeeId, 10, null, null, null
    )).thenReturn(response);

    // when
    var result = mockMvc.perform(
        get("/api/follows/followers")
            .principal(authentication(followerId))
            .param("followeeId", followeeId.toString())
            .param("limit", "10")
    );

    // then
    result.andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.totalCount").value(0));

    verify(followService).getFollowers(
        followeeId, 10, null, null, null
    );
  }

  @Test
  void 미인증_사용자는_팔로워_목록을_조회할_수_없다()
      throws Exception {
    // given

    // when
    var result = mockMvc.perform(
        get("/api/follows/followers")
            .param("followeeId", followeeId.toString())
            .param("limit", "10")
    );

    // then
    result.andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.exceptionName")
            .value("AUTHENTICATION_REQUIRED"));

    verifyNoInteractions(followService);
  }

  @Test
  void 팔로워_조회에서_필수_파라미터_누락이나_형식_오류는_400을_반환한다()
      throws Exception {
    // given

    // when
    var missingFolloweeId = mockMvc.perform(
        get("/api/follows/followers")
            .principal(authentication(followerId))
            .param("limit", "10")
    );

    var missingLimit = mockMvc.perform(
        get("/api/follows/followers")
            .principal(authentication(followerId))
            .param("followeeId", followeeId.toString())
    );

    var invalidFolloweeId = mockMvc.perform(
        get("/api/follows/followers")
            .principal(authentication(followerId))
            .param("followeeId", "invalid-uuid")
            .param("limit", "10")
    );

    var invalidLimit = mockMvc.perform(
        get("/api/follows/followers")
            .principal(authentication(followerId))
            .param("followeeId", followeeId.toString())
            .param("limit", "invalid")
    );

    // then
    missingFolloweeId.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    missingLimit.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    invalidFolloweeId.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    invalidLimit.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    verifyNoInteractions(followService);
  }

  @Test
  void 팔로잉_목록_조회에_성공하면_200과_커서_응답을_반환한다()
      throws Exception {
    // given
    UUID firstFollowId = UUID.randomUUID();
    UUID lastFollowId = UUID.randomUUID();
    UUID firstFolloweeId = UUID.randomUUID();
    UUID lastFolloweeId = UUID.randomUUID();
    String nextCursor = "2026-11-12T01:00:00Z";

    FollowDto firstDto = new FollowDto(
        firstFollowId,
        new UserSummary(firstFolloweeId, "김민수", "/images/minsu.png"),
        new UserSummary(followerId, "나", null)
    );

    FollowDto secondDto = new FollowDto(
        lastFollowId,
        new UserSummary(lastFolloweeId, "이민지", "/images/minji.png"),
        new UserSummary(followerId, "나", null)
    );

    CursorResponse<FollowDto> response = new CursorResponse<>(
        List.of(firstDto, secondDto),
        nextCursor,
        lastFollowId,
        true,
        5L,
        "createdAt",
        "DESCENDING"
    );

    when(followService.getFollowings(
        followerId, 2, null, null, "민"
    )).thenReturn(response);

    // when
    var result = mockMvc.perform(
        get("/api/follows/followings")
            .principal(authentication(followerId))
            .param("followerId", followerId.toString())
            .param("limit", "2")
            .param("nameLike", "민")
    );

    // then
    result.andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id")
            .value(firstFollowId.toString()))
        .andExpect(jsonPath("$.data[0].followee.userId")
            .value(firstFolloweeId.toString()))
        .andExpect(jsonPath("$.data[0].followee.name")
            .value("김민수"))
        .andExpect(jsonPath("$.data[0].followee.profileImageUrl")
            .value("/images/minsu.png"))
        .andExpect(jsonPath("$.data[0].follower.userId")
            .value(followerId.toString()))
        .andExpect(jsonPath("$.data[1].followee.name")
            .value("이민지"))
        .andExpect(jsonPath("$.nextCursor").value(nextCursor))
        .andExpect(jsonPath("$.nextIdAfter")
            .value(lastFollowId.toString()))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.totalCount").value(5))
        .andExpect(jsonPath("$.sortBy").value("createdAt"))
        .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));

    verify(followService).getFollowings(
        followerId, 2, null, null, "민"
    );
  }

  @Test
  void 팔로잉_조회에서_커서와_검색어를_서비스에_전달한다()
      throws Exception {
    // given
    UUID idAfter = UUID.randomUUID();
    String cursor = "2026-11-12T01:00:00Z";

    CursorResponse<FollowDto> response = new CursorResponse<>(
        List.of(),
        null,
        null,
        false,
        3L,
        "createdAt",
        "DESCENDING"
    );

    when(followService.getFollowings(
        followerId, 10, cursor, idAfter, "김"
    )).thenReturn(response);

    // when
    var result = mockMvc.perform(
        get("/api/follows/followings")
            .principal(authentication(followerId))
            .param("followerId", followerId.toString())
            .param("limit", "10")
            .param("cursor", cursor)
            .param("idAfter", idAfter.toString())
            .param("nameLike", "김")
    );

    // then
    result.andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.nextCursor").value(nullValue()))
        .andExpect(jsonPath("$.nextIdAfter").value(nullValue()))
        .andExpect(jsonPath("$.totalCount").value(3));

    verify(followService).getFollowings(
        followerId, 10, cursor, idAfter, "김"
    );
  }

  @Test
  void 팔로잉_조회에서_선택_파라미터를_생략할_수_있다()
      throws Exception {
    // given
    CursorResponse<FollowDto> response = new CursorResponse<>(
        List.of(),
        null,
        null,
        false,
        0L,
        "createdAt",
        "DESCENDING"
    );

    when(followService.getFollowings(
        followerId, 10, null, null, null
    )).thenReturn(response);

    // when
    var result = mockMvc.perform(
        get("/api/follows/followings")
            .principal(authentication(followerId))
            .param("followerId", followerId.toString())
            .param("limit", "10")
    );

    // then
    result.andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.totalCount").value(0));

    verify(followService).getFollowings(
        followerId, 10, null, null, null
    );
  }

  @Test
  void 미인증_사용자는_팔로잉_목록을_조회할_수_없다()
      throws Exception {
    // given

    // when
    var result = mockMvc.perform(
        get("/api/follows/followings")
            .param("followerId", followerId.toString())
            .param("limit", "10")
    );

    // then
    result.andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.exceptionName")
            .value("AUTHENTICATION_REQUIRED"));

    verifyNoInteractions(followService);
  }

  @Test
  void 팔로잉_조회에서_필수_파라미터_누락이나_형식_오류는_400을_반환한다()
      throws Exception {
    // given

    // when
    var missingFollowerId = mockMvc.perform(
        get("/api/follows/followings")
            .principal(authentication(followerId))
            .param("limit", "10")
    );

    var missingLimit = mockMvc.perform(
        get("/api/follows/followings")
            .principal(authentication(followerId))
            .param("followerId", followerId.toString())
    );

    var invalidFollowerId = mockMvc.perform(
        get("/api/follows/followings")
            .principal(authentication(followerId))
            .param("followerId", "invalid-uuid")
            .param("limit", "10")
    );

    var invalidLimit = mockMvc.perform(
        get("/api/follows/followings")
            .principal(authentication(followerId))
            .param("followerId", followerId.toString())
            .param("limit", "invalid")
    );

    // then
    missingFollowerId.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    missingLimit.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    invalidFollowerId.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    invalidLimit.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    verifyNoInteractions(followService);
  }

  @Test
  void 인증되지_않은_Authentication_객체는_팔로잉_조회를_거부한다()
      throws Exception {
    // given
    Authentication unauthenticated =
        new UsernamePasswordAuthenticationToken(
            authentication(followerId).getPrincipal(),
            null
        );

    // when
    var result = mockMvc.perform(
        get("/api/follows/followings")
            .principal(unauthenticated)
            .param("followerId", followerId.toString())
            .param("limit", "10")
    );

    // then
    result.andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.exceptionName")
            .value("AUTHENTICATION_REQUIRED"));

    verifyNoInteractions(followService);
  }

  @Test
  void 잘못된_Principal_타입은_팔로잉_조회를_거부한다()
      throws Exception {
    // given
    Authentication invalidPrincipal =
        new UsernamePasswordAuthenticationToken(
            "invalid-principal",
            null,
            authentication(followerId).getAuthorities()
        );

    // when
    var result = mockMvc.perform(
        get("/api/follows/followings")
            .principal(invalidPrincipal)
            .param("followerId", followerId.toString())
            .param("limit", "10")
    );

    // then
    result.andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.exceptionName")
            .value("AUTHENTICATION_REQUIRED"));

    verifyNoInteractions(followService);
  }
}
