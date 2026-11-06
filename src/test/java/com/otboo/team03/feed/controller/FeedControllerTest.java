package com.otboo.team03.feed.controller;

import com.otboo.team03.clothes.dto.response.ClothesAttributeWithDefDto;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.feed.dto.request.CommentCreateRequest;
import com.otboo.team03.feed.dto.request.FeedCreateRequest;
import com.otboo.team03.feed.dto.request.FeedUpdateRequest;
import com.otboo.team03.feed.dto.response.AuthorDto;
import com.otboo.team03.feed.dto.response.CommentDto;
import com.otboo.team03.feed.dto.response.FeedDto;
import com.otboo.team03.feed.dto.response.OotdDto;
import com.otboo.team03.feed.dto.response.PrecipitationDto;
import com.otboo.team03.feed.dto.response.TemperatureDto;
import com.otboo.team03.feed.dto.response.WeatherSummaryDto;
import com.otboo.team03.feed.service.FeedService;
import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.GlobalExceptionHandler;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.UserRole;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FeedControllerTest {

  @Mock
  private FeedService feedService;

  @InjectMocks
  private FeedController feedController;

  private MockMvc mockMvc;
  private LocalValidatorFactoryBean validator;

  private final UUID authorId = UUID.randomUUID();
  private final UUID weatherId = UUID.randomUUID();
  private final UUID clothesId = UUID.randomUUID();
  private final UUID feedId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();

    mockMvc = MockMvcBuilders
        .standaloneSetup(feedController)
        .setControllerAdvice(new GlobalExceptionHandler())
        .setValidator(validator)
        .build();
  }

  @AfterEach
  void tearDown() {
    validator.close();
  }

  @Test
  void 피드_등록에_성공하면_201과_FeedDto를_반환한다()
      throws Exception {
    // given
    when(feedService.create(any(FeedCreateRequest.class)))
        .thenReturn(createResponse());

    // when
    var result = mockMvc.perform(
        post("/api/feeds")
            .principal(authentication(authorId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(createRequestJson(authorId))
    );

    // then
    result.andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(feedId.toString()))
        .andExpect(jsonPath("$.content").value("오늘의 코디"))
        .andExpect(jsonPath("$.author.userId")
            .value(authorId.toString()))
        .andExpect(jsonPath("$.author.name")
            .value("테스트 사용자"))
        .andExpect(jsonPath("$.weather.weatherId")
            .value(weatherId.toString()))
        .andExpect(jsonPath("$.weather.skyStatus")
            .value("CLEAR"))
        .andExpect(jsonPath("$.weather.temperature.current")
            .value(20.0))
        .andExpect(jsonPath("$.ootds[0].clothesId")
            .value(clothesId.toString()))
        .andExpect(jsonPath("$.ootds[0].type").value("TOP"))
        .andExpect(jsonPath("$.ootds[0].attributes[0].value")
            .value("흰색"))
        .andExpect(jsonPath("$.likeCount").value(0))
        .andExpect(jsonPath("$.commentCount").value(0))
        .andExpect(jsonPath("$.likedByMe").value(false));

    verify(feedService).create(new FeedCreateRequest(
        authorId,
        weatherId,
        List.of(clothesId),
        "오늘의 코디"
    ));
  }

  @Test
  void 다른_사용자_ID로_피드를_등록하면_403을_반환한다()
      throws Exception {
    // given
    UUID otherUserId = UUID.randomUUID();

    // when
    var result = mockMvc.perform(
        post("/api/feeds")
            .principal(authentication(authorId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(createRequestJson(otherUserId))
    );

    // then
    result.andExpect(status().isForbidden())
        .andExpect(jsonPath("$.exceptionName")
            .value("ACCESS_DENIED"));

    verifyNoInteractions(feedService);
  }

  @Test
  void 인증_정보가_없으면_401을_반환한다()
      throws Exception {
    // given
    String requestJson = createRequestJson(authorId);

    // when
    var result = mockMvc.perform(
        post("/api/feeds")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
    );

    // then
    result.andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.exceptionName")
            .value("AUTHENTICATION_REQUIRED"));

    verifyNoInteractions(feedService);
  }

  @Test
  void 작성자_ID가_없으면_400을_반환한다()
      throws Exception {
    // given
    String requestJson = """
                {
                  "weatherId": "%s",
                  "clothesIds": [],
                  "content": "오늘의 코디"
                }
                """.formatted(weatherId);

    // when
    var result = mockMvc.perform(
        post("/api/feeds")
            .principal(authentication(authorId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
    );

    // then
    result.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"))
        .andExpect(jsonPath("$.details.authorId").exists());

    verifyNoInteractions(feedService);
  }

  @Test
  void 내용이_공백이면_400을_반환한다()
      throws Exception {
    // given
    String requestJson = """
                {
                  "authorId": "%s",
                  "weatherId": "%s",
                  "clothesIds": [],
                  "content": "   "
                }
                """.formatted(authorId, weatherId);

    // when
    var result = mockMvc.perform(
        post("/api/feeds")
            .principal(authentication(authorId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
    );

    // then
    result.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.content").exists());

    verifyNoInteractions(feedService);
  }

  @Test
  void 서비스에서_입력값_예외가_발생하면_400을_반환한다()
      throws Exception {
    // given
    when(feedService.create(any(FeedCreateRequest.class)))
        .thenThrow(
            new OtbooException(ErrorCode.INVALID_INPUT_VALUE)
        );

    String requestJson = """
                {
                  "authorId": "%s",
                  "weatherId": "%s",
                  "clothesIds": [],
                  "content": "오늘의 코디"
                }
                """.formatted(authorId, weatherId);

    // when
    var result = mockMvc.perform(
        post("/api/feeds")
            .principal(authentication(authorId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
    );

    // then
    result.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    verify(feedService).create(new FeedCreateRequest(
        authorId,
        weatherId,
        List.of(),
        "오늘의 코디"
    ));
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

  private String createRequestJson(UUID requestedAuthorId) {
    return """
                {
                  "authorId": "%s",
                  "weatherId": "%s",
                  "clothesIds": ["%s"],
                  "content": "오늘의 코디"
                }
                """.formatted(
        requestedAuthorId,
        weatherId,
        clothesId
    );
  }

  private FeedDto createResponse() {
    return new FeedDto(
        feedId,
        Instant.parse("2026-10-29T00:00:00Z"),
        Instant.parse("2026-10-29T00:00:00Z"),
        new AuthorDto(
            authorId,
            "테스트 사용자",
            null
        ),
        new WeatherSummaryDto(
            weatherId,
            "CLEAR",
            new PrecipitationDto("NONE", 0.0, 0.0),
            new TemperatureDto(20.0, null, null, null)
        ),
        List.of(
            new OotdDto(
                clothesId,
                "흰색 티셔츠",
                "/images/shirt.png",
                ClothesType.TOP,
                List.of(
                    new ClothesAttributeWithDefDto(
                        UUID.randomUUID(),
                        "색상",
                        List.of("흰색", "검정"),
                        "흰색"
                    )
                )
            )
        ),
        "오늘의 코디",
        0L,
        0,
        false
    );
  }

  @Test
  void 작성자가_피드를_수정하면_200과_FeedDto를_반환한다()
      throws Exception {
    // given
    FeedDto original = createResponse();

    FeedDto updatedResponse = new FeedDto(
        original.id(),
        original.createdAt(),
        original.updatedAt(),
        original.author(),
        original.weather(),
        original.ootds(),
        "수정 후 내용",
        original.likeCount(),
        original.commentCount(),
        original.likedByMe()
    );

    when(feedService.update(
        eq(feedId),
        eq(authorId),
        any(FeedUpdateRequest.class)
    )).thenReturn(updatedResponse);

    // when
    var result = mockMvc.perform(
        patch("/api/feeds/{feedId}", feedId)
            .principal(authentication(authorId))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
              {
                "content": "수정 후 내용"
              }
              """)
    );

    // then
    result.andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(feedId.toString()))
        .andExpect(jsonPath("$.content").value("수정 후 내용"));

    verify(feedService).update(
        feedId,
        authorId,
        new FeedUpdateRequest("수정 후 내용")
    );
  }

  @Test
  void 작성자가_피드를_삭제하면_204를_반환한다()
      throws Exception {
    // given
    UUID targetFeedId = UUID.randomUUID();

    // when
    var result = mockMvc.perform(
        delete("/api/feeds/{feedId}", targetFeedId)
            .principal(authentication(authorId))
    );

    // then
    result.andExpect(status().isNoContent())
        .andExpect(content().string(""));

    verify(feedService).delete(targetFeedId, authorId);
  }

  @Test
  void 수정과_삭제에서_유효하지_않은_인증은_401을_반환한다()
      throws Exception {
    // given
    Authentication[] invalidAuthentications = {
        null,
        new UsernamePasswordAuthenticationToken("user", null),
        new UsernamePasswordAuthenticationToken(
            "user", null, List.of()
        )
    };

    // when
    for (Authentication authentication : invalidAuthentications) {
      var patchRequest = patch("/api/feeds/{feedId}", feedId)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "content": "수정 내용"
            }
            """);

      var deleteRequest = delete("/api/feeds/{feedId}", feedId);

      if (authentication != null) {
        patchRequest.principal(authentication);
        deleteRequest.principal(authentication);
      }

      // then
      mockMvc.perform(patchRequest)
          .andExpect(status().isUnauthorized());

      mockMvc.perform(deleteRequest)
          .andExpect(status().isUnauthorized());
    }

    verifyNoInteractions(feedService);
  }

  @Test
  void 피드_좋아요에_성공하면_204를_반환한다()
      throws Exception {
    // given
    UUID targetFeedId = UUID.randomUUID();

    // when
    var result = mockMvc.perform(
        post("/api/feeds/{feedId}/like", targetFeedId)
            .principal(authentication(authorId))
    );

    // then
    result.andExpect(status().isNoContent())
        .andExpect(content().string(""));

    verify(feedService).like(targetFeedId, authorId);
  }

  @Test
  void 피드_좋아요_취소에_성공하면_204를_반환한다()
      throws Exception {
    // given
    UUID targetFeedId = UUID.randomUUID();

    // when
    var result = mockMvc.perform(
        delete("/api/feeds/{feedId}/like", targetFeedId)
            .principal(authentication(authorId))
    );

    // then
    result.andExpect(status().isNoContent())
        .andExpect(content().string(""));

    verify(feedService).unlike(targetFeedId, authorId);
  }

  @Test
  void 좋아요_등록과_취소에서_유효하지_않은_인증은_401을_반환한다()
      throws Exception {
    // given
    Authentication[] invalidAuthentications = {
        null,
        new UsernamePasswordAuthenticationToken("user", null),
        new UsernamePasswordAuthenticationToken(
            "user", null, List.of()
        )
    };

    // when
    for (Authentication authentication : invalidAuthentications) {
      var postRequest = post("/api/feeds/{feedId}/like", feedId);
      var deleteRequest = delete("/api/feeds/{feedId}/like", feedId);

      if (authentication != null) {
        postRequest.principal(authentication);
        deleteRequest.principal(authentication);
      }

      // then
      mockMvc.perform(postRequest)
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.exceptionName")
              .value("AUTHENTICATION_REQUIRED"));

      mockMvc.perform(deleteRequest)
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.exceptionName")
              .value("AUTHENTICATION_REQUIRED"));
    }

    verifyNoInteractions(feedService);
  }

  @Test
  void 존재하지_않는_피드의_좋아요_등록과_취소는_404를_반환한다()
      throws Exception {
    // given
    doThrow(new OtbooException(ErrorCode.NOT_FOUND_RESOURCE))
        .when(feedService).like(feedId, authorId);

    doThrow(new OtbooException(ErrorCode.NOT_FOUND_RESOURCE))
        .when(feedService).unlike(feedId, authorId);

    // when
    var postResult = mockMvc.perform(
        post("/api/feeds/{feedId}/like", feedId)
            .principal(authentication(authorId))
    );

    var deleteResult = mockMvc.perform(
        delete("/api/feeds/{feedId}/like", feedId)
            .principal(authentication(authorId))
    );

    // then
    postResult.andExpect(status().isNotFound())
        .andExpect(jsonPath("$.exceptionName")
            .value("NOT_FOUND_RESOURCE"));

    deleteResult.andExpect(status().isNotFound())
        .andExpect(jsonPath("$.exceptionName")
            .value("NOT_FOUND_RESOURCE"));

    verify(feedService).like(feedId, authorId);
    verify(feedService).unlike(feedId, authorId);
  }

  @Test
  void 피드_댓글_등록에_성공하면_200과_CommentDto를_반환한다()
      throws Exception {
    // given
    UUID commentId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-11-05T00:00:00Z");

    CommentDto expectedResponse = new CommentDto(
        commentId,
        createdAt,
        feedId,
        new AuthorDto(
            authorId,
            "테스트 사용자",
            "/images/profile.png"
        ),
        "좋은 코디네요!"
    );

    when(feedService.createComment(
        eq(feedId),
        eq(authorId),
        any(CommentCreateRequest.class)
    )).thenReturn(expectedResponse);

    String requestJson = """
      {
        "feedId": "%s",
        "authorId": "%s",
        "content": "좋은 코디네요!"
      }
      """.formatted(feedId, authorId);

    // when
    var result = mockMvc.perform(
        post("/api/feeds/{feedId}/comments", feedId)
            .principal(authentication(authorId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
    );

    // then
    result.andExpect(status().isOk())
        .andExpect(jsonPath("$.id")
            .value(commentId.toString()))
        .andExpect(jsonPath("$.createdAt")
            .value(createdAt.toString()))
        .andExpect(jsonPath("$.feedId")
            .value(feedId.toString()))
        .andExpect(jsonPath("$.author.userId")
            .value(authorId.toString()))
        .andExpect(jsonPath("$.author.name")
            .value("테스트 사용자"))
        .andExpect(jsonPath("$.author.profileImageUrl")
            .value("/images/profile.png"))
        .andExpect(jsonPath("$.content")
            .value("좋은 코디네요!"));

    verify(feedService).createComment(
        feedId,
        authorId,
        new CommentCreateRequest(
            feedId,
            authorId,
            "좋은 코디네요!"
        )
    );
  }

  @Test
  void 댓글_등록에서_유효하지_않은_인증은_401을_반환한다()
      throws Exception {
    // given
    Authentication[] invalidAuthentications = {
        null,
        new UsernamePasswordAuthenticationToken("user", null),
        new UsernamePasswordAuthenticationToken(
            "user", null, List.of()
        )
    };

    String requestJson = """
      {
        "feedId": "%s",
        "authorId": "%s",
        "content": "좋은 코디네요!"
      }
      """.formatted(feedId, authorId);

    // when
    for (Authentication authentication : invalidAuthentications) {
      var request = post("/api/feeds/{feedId}/comments", feedId)
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestJson);

      if (authentication != null) {
        request.principal(authentication);
      }

      // then
      mockMvc.perform(request)
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.exceptionName")
              .value("AUTHENTICATION_REQUIRED"));
    }

    verifyNoInteractions(feedService);
  }

  @Test
  void 댓글_작성자가_인증_사용자와_다르면_403을_반환한다()
      throws Exception {
    // given
    UUID otherAuthorId = UUID.randomUUID();

    CommentCreateRequest request = new CommentCreateRequest(
        feedId,
        otherAuthorId,
        "좋은 코디네요!"
    );

    when(feedService.createComment(feedId, authorId, request))
        .thenThrow(new OtbooException(ErrorCode.ACCESS_DENIED));

    String requestJson = """
      {
        "feedId": "%s",
        "authorId": "%s",
        "content": "좋은 코디네요!"
      }
      """.formatted(feedId, otherAuthorId);

    // when
    var result = mockMvc.perform(
        post("/api/feeds/{feedId}/comments", feedId)
            .principal(authentication(authorId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
    );

    // then
    result.andExpect(status().isForbidden())
        .andExpect(jsonPath("$.exceptionName")
            .value("ACCESS_DENIED"));

    verify(feedService).createComment(feedId, authorId, request);
  }

  @Test
  void 존재하지_않는_피드에_댓글을_등록하면_404를_반환한다()
      throws Exception {
    // given
    UUID nonexistentFeedId = UUID.randomUUID();

    CommentCreateRequest request = new CommentCreateRequest(
        nonexistentFeedId,
        authorId,
        "좋은 코디네요!"
    );

    when(feedService.createComment(
        nonexistentFeedId,
        authorId,
        request
    )).thenThrow(
        new OtbooException(ErrorCode.NOT_FOUND_RESOURCE)
    );

    String requestJson = """
      {
        "feedId": "%s",
        "authorId": "%s",
        "content": "좋은 코디네요!"
      }
      """.formatted(nonexistentFeedId, authorId);

    // when
    var result = mockMvc.perform(
        post("/api/feeds/{feedId}/comments", nonexistentFeedId)
            .principal(authentication(authorId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
    );

    // then
    result.andExpect(status().isNotFound())
        .andExpect(jsonPath("$.exceptionName")
            .value("NOT_FOUND_RESOURCE"));

    verify(feedService).createComment(
        nonexistentFeedId,
        authorId,
        request
    );
  }

  @Test
  void 공백_댓글을_등록하면_400을_반환한다()
      throws Exception {
    // given
    CommentCreateRequest request = new CommentCreateRequest(
        feedId,
        authorId,
        "   "
    );

    when(feedService.createComment(feedId, authorId, request))
        .thenThrow(
            new OtbooException(ErrorCode.INVALID_INPUT_VALUE)
        );

    String requestJson = """
      {
        "feedId": "%s",
        "authorId": "%s",
        "content": "   "
      }
      """.formatted(feedId, authorId);

    // when
    var result = mockMvc.perform(
        post("/api/feeds/{feedId}/comments", feedId)
            .principal(authentication(authorId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
    );

    // then
    result.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    verify(feedService).createComment(feedId, authorId, request);
  }

  @Test
  void 피드_댓글_첫_페이지_조회에_성공하면_200과_커서_응답을_반환한다()
      throws Exception {
    // given
    UUID commentId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-11-05T03:00:00Z");

    CommentDto commentDto = new CommentDto(
        commentId,
        createdAt,
        feedId,
        new AuthorDto(
            authorId,
            "테스트 사용자",
            "/images/profile.png"
        ),
        "좋은 코디네요!"
    );

    CursorResponse<CommentDto> expectedResponse =
        new CursorResponse<>(
            List.of(commentDto),
            createdAt.toString(),
            commentId,
            true,
            2L,
            "createdAt",
            "DESCENDING"
        );

    when(feedService.getFeedComments(feedId, 1, null, null))
        .thenReturn(expectedResponse);

    // when
    var result = mockMvc.perform(
        get("/api/feeds/{feedId}/comments", feedId)
            .param("limit", "1")
            .principal(authentication(authorId))
    );

    // then
    result.andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id")
            .value(commentId.toString()))
        .andExpect(jsonPath("$.data[0].feedId")
            .value(feedId.toString()))
        .andExpect(jsonPath("$.data[0].author.userId")
            .value(authorId.toString()))
        .andExpect(jsonPath("$.data[0].author.name")
            .value("테스트 사용자"))
        .andExpect(jsonPath("$.data[0].author.profileImageUrl")
            .value("/images/profile.png"))
        .andExpect(jsonPath("$.data[0].content")
            .value("좋은 코디네요!"))
        .andExpect(jsonPath("$.nextCursor")
            .value(createdAt.toString()))
        .andExpect(jsonPath("$.nextIdAfter")
            .value(commentId.toString()))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.totalCount").value(2))
        .andExpect(jsonPath("$.sortBy").value("createdAt"))
        .andExpect(jsonPath("$.sortDirection")
            .value("DESCENDING"));

    verify(feedService).getFeedComments(
        feedId, 1, null, null
    );
  }

  @Test
  void 피드_댓글_조회에서_커서와_ID를_서비스에_전달한다()
      throws Exception {
    // given
    UUID cursorId = UUID.randomUUID();
    UUID commentId = UUID.randomUUID();

    String cursor = "2026-11-05T03:00:00Z";

    CommentDto commentDto = new CommentDto(
        commentId,
        Instant.parse("2026-11-05T02:00:00Z"),
        feedId,
        new AuthorDto(authorId, "테스트 사용자", null),
        "다음 페이지 댓글"
    );

    CursorResponse<CommentDto> expectedResponse =
        new CursorResponse<>(
            List.of(commentDto),
            null,
            null,
            false,
            2L,
            "createdAt",
            "DESCENDING"
        );

    when(feedService.getFeedComments(
        feedId, 1, cursor, cursorId
    )).thenReturn(expectedResponse);

    // when
    var result = mockMvc.perform(
        get("/api/feeds/{feedId}/comments", feedId)
            .param("limit", "1")
            .param("cursor", cursor)
            .param("idAfter", cursorId.toString())
            .principal(authentication(authorId))
    );

    // then
    result.andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id")
            .value(commentId.toString()))
        .andExpect(jsonPath("$.data[0].content")
            .value("다음 페이지 댓글"))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.totalCount").value(2));

    verify(feedService).getFeedComments(
        feedId, 1, cursor, cursorId
    );
  }
}
