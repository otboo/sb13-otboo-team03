package com.otboo.team03.recommendation.service;

import com.otboo.team03.global.exception.InvalidLlmResponseException;
import com.otboo.team03.recommendation.client.GroqOutfitClient;
import com.otboo.team03.recommendation.config.RecommendationLlmProperties;
import com.otboo.team03.recommendation.client.dto.LlmOutfitDecision;
import com.otboo.team03.recommendation.validation.LlmOutfitValidator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

import static com.otboo.team03.recommendation.support.LlmTestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LlmOutfitRecommenderTest {
    private final GroqOutfitClient client = mock(GroqOutfitClient.class);
    private final LlmOutfitValidator validator = new LlmOutfitValidator();

    @Test
    void 비활성화하면_외부_API를_호출하지_않는다() {
        assertThat(recommender(properties(false)).recommend(input())).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void 유효한_자유_속성_분석과_추천을_반환한다() {
        when(client.serializeInput(input())).thenReturn("input");
        when(client.recommend(input(), "input")).thenReturn(decision());
        assertThat(recommender(properties(true)).recommend(input())).contains(decision());
    }

    @Test
    void 입력_의상수나_길이가_초과하면_일부만_보내지_않고_기본_추천으로_돌아간다() {
        var countLimit = new RecommendationLlmProperties(true, "model", 2, 100, 100);
        assertThat(recommender(countLimit).recommend(input())).isEmpty();
        verifyNoInteractions(client);

        when(client.serializeInput(input())).thenReturn("x".repeat(12001));
        assertThat(recommender(properties(true)).recommend(input())).isEmpty();
        verify(client, never()).recommend(any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500})
    void 공급자_HTTP_오류는_재시도_없이_기본_추천으로_돌아간다(int status) {
        when(client.serializeInput(input())).thenReturn("input");
        when(client.recommend(input(), "input")).thenThrow(new RestClientResponseException(
                "test", status, "error", null, null, null));
        assertThat(recommender(properties(true)).recommend(input())).isEmpty();
        verify(client, times(1)).recommend(input(), "input");
    }

    @Test
    void 시간초과와_불완전_응답은_기본_추천으로_돌아간다() {
        when(client.serializeInput(input())).thenReturn("input");
        when(client.recommend(input(), "input")).thenThrow(new ResourceAccessException("timeout"))
                .thenThrow(new InvalidLlmResponseException());
        assertThat(recommender(properties(true)).recommend(input())).isEmpty();
        assertThat(recommender(properties(true)).recommend(input())).isEmpty();
    }

    @Test
    void JSON_파싱_실패도_기본_추천으로_돌아간다() {
        when(client.serializeInput(input())).thenReturn("input");
        when(client.recommend(input(), "input")).thenAnswer(invocation ->
                JsonMapper.builder().build().readValue("invalid json", LlmOutfitDecision.class));
        assertThat(recommender(properties(true)).recommend(input())).isEmpty();
    }

    @Test
    void 존재하지_않는_의상이나_근거가_없는_결과는_거부한다() {
        when(client.serializeInput(input())).thenReturn("input");
        when(client.recommend(input(), "input")).thenReturn(new LlmOutfitDecision(
                List.of(TOP_ID), List.of(), "상의만 선택"));
        assertThat(recommender(properties(true)).recommend(input())).isEmpty();
    }

    @Test
    void 예상하지_못한_코드_오류는_숨기지_않는다() {
        when(client.serializeInput(input())).thenThrow(new IllegalStateException("bug"));
        assertThatThrownBy(() -> recommender(properties(true)).recommend(input()))
                .isInstanceOf(IllegalStateException.class);
    }

    private LlmOutfitRecommender recommender(RecommendationLlmProperties properties) {
        return new LlmOutfitRecommender(properties, client, validator);
    }
}
