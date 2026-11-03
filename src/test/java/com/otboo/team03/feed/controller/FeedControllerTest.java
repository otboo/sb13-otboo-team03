package com.otboo.team03.feed.controller;

import com.otboo.team03.clothes.dto.response.ClothesAttributeWithDefDto;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.feed.dto.request.FeedCreateRequest;
import com.otboo.team03.feed.dto.response.AuthorDto;
import com.otboo.team03.feed.dto.response.FeedDto;
import com.otboo.team03.feed.dto.response.OotdDto;
import com.otboo.team03.feed.dto.response.PrecipitationDto;
import com.otboo.team03.feed.dto.response.TemperatureDto;
import com.otboo.team03.feed.dto.response.WeatherSummaryDto;
import com.otboo.team03.feed.service.FeedService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
