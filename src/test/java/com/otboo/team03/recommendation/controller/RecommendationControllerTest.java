package com.otboo.team03.recommendation.controller;

import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.feed.dto.response.OotdDto;
import com.otboo.team03.global.exception.GlobalExceptionHandler;
import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.recommendation.dto.RecommendationDto;
import com.otboo.team03.recommendation.service.RecommendationService;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RecommendationControllerTest {

    @Mock private RecommendationService service;

    private MockMvc mvc;
    private final UUID userId = UUID.randomUUID();
    private final UUID weatherId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RecommendationController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void 인증된_사용자_ID로_추천하고_응답_JSON을_반환한다() throws Exception {
        UUID clothesId = UUID.randomUUID();
        var ootd = new OotdDto(clothesId, "원피스", "https://example.com/image",
                ClothesType.DRESS, List.of());
        when(service.recommend(weatherId, userId))
                .thenReturn(new RecommendationDto(weatherId, userId, List.of(ootd)));

        mvc.perform(get("/api/recommendations").param("weatherId", weatherId.toString())
                        .param("userId", UUID.randomUUID().toString()).principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weatherId").value(weatherId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.clothes[0].clothesId").value(clothesId.toString()))
                .andExpect(jsonPath("$.clothes[0].name").value("원피스"))
                .andExpect(jsonPath("$.clothes[0].type").value("DRESS"))
                .andExpect(jsonPath("$.clothes[0].imageUrl").value(ootd.imageUrl()))
                .andExpect(jsonPath("$.clothes[0].attributes").isEmpty());
        verify(service).recommend(weatherId, userId);
    }

    @Test
    void 인증정보가_없으면_401을_반환한다() throws Exception {
        mvc.perform(get("/api/recommendations").param("weatherId", weatherId.toString()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void 인증되지_않은_토큰이면_401을_반환한다() throws Exception {
        var unauthenticated = new UsernamePasswordAuthenticationToken("user", "password");
        mvc.perform(get("/api/recommendations").param("weatherId", weatherId.toString())
                        .principal(unauthenticated))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void 인증된_principal이_사용자_타입이_아니면_401을_반환한다() throws Exception {
        var invalid = new UsernamePasswordAuthenticationToken("user", null, List.of());
        mvc.perform(get("/api/recommendations").param("weatherId", weatherId.toString())
                        .principal(invalid))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void weatherId가_없으면_400을_반환한다() throws Exception {
        mvc.perform(get("/api/recommendations").principal(authentication()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details.weatherId").value("필수 요청 파라미터입니다."));
        verifyNoInteractions(service);
    }

    @Test
    void weatherId가_UUID가_아니면_400을_반환한다() throws Exception {
        mvc.perform(get("/api/recommendations").param("weatherId", "invalid")
                        .principal(authentication()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details.weatherId")
                        .value("요청 값의 형식 또는 허용 범위가 올바르지 않습니다."));
        verifyNoInteractions(service);
    }

    private Authentication authentication() {
        var dto = new UserDto(userId, Instant.now(), "user@example.com", "사용자", UserRole.USER, false);
        var details = new OtbooUserDetails(dto, "encoded-password");
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }
}
