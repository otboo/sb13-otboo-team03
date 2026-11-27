package com.otboo.team03.recommendation.service;

import com.otboo.team03.global.exception.llm.GroqRequestException;
import com.otboo.team03.global.exception.llm.InvalidLlmResponseException;
import com.otboo.team03.recommendation.client.GroqOutfitClient;
import com.otboo.team03.recommendation.config.RecommendationLlmProperties;
import com.otboo.team03.recommendation.client.dto.LlmOutfitDecision;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import com.otboo.team03.recommendation.validation.LlmOutfitValidator;
import com.otboo.team03.recommendation.rag.OutfitReferenceProvider;
import com.otboo.team03.recommendation.rag.StyleReference;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

import static com.otboo.team03.recommendation.support.LlmTestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class LlmOutfitRecommenderTest {
    private final GroqOutfitClient client = mock(GroqOutfitClient.class);
    private final LlmOutfitValidator validator = new LlmOutfitValidator();
    private final OutfitReferenceProvider references = mock(OutfitReferenceProvider.class);

    @Test
    void 비활성화하면_외부_API를_호출하지_않는다() {
        assertThat(recommender(properties(false)).recommend(input())).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void 빈_옷장이면_외부_API를_호출하지_않는다() {
        var emptyWardrobe = new LlmRecommendationInput(input().weather(), 3,
                input().previousOutfitIds(), List.of(), List.of());

        assertThat(recommender(properties(true)).recommend(emptyWardrobe)).isEmpty();
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
    @ValueSource(ints = {400, 401, 429, 500})
    void 공급자_HTTP_오류는_재시도_없이_기본_추천으로_돌아간다(int status) {
        when(client.serializeInput(input())).thenReturn("input");
        when(client.recommend(input(), "input")).thenThrow(new RestClientResponseException(
                "test", status, "error", null, null, null));
        assertThat(recommender(properties(true)).recommend(input())).isEmpty();
        verify(client, times(1)).recommend(input(), "input");
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 429, 500})
    void Groq_HTTP_오류는_안전한_진단_로그를_남기고_재시도하지_않는다(int status, CapturedOutput output) {
        when(client.serializeInput(input())).thenReturn("비공개_옷장_입력");
        when(client.recommend(input(), "비공개_옷장_입력"))
                .thenThrow(new GroqRequestException(status, "invalid_request_error", "json_validate_failed"));

        assertThat(recommender(properties(true)).recommend(input())).isEmpty();

        verify(client, times(1)).recommend(input(), "비공개_옷장_입력");
        assertThat(output.getAll()).contains("status=" + status,
                "providerType=invalid_request_error", "providerCode=json_validate_failed")
                .doesNotContain("비공개_옷장_입력", "GroqRequestException:");
    }

    @Test
    void 공급자_오류_필드에_민감한_값이_있어도_로그에는_기록하지_않는다(CapturedOutput output) {
        when(client.serializeInput(input())).thenReturn("input");
        when(client.recommend(input(), "input"))
                .thenThrow(new GroqRequestException(400, "gsk_test_secret", "비공개_옷_제목"));

        assertThat(recommender(properties(true)).recommend(input())).isEmpty();

        assertThat(output.getAll()).contains("status=400", "providerType=unknown", "providerCode=unknown")
                .doesNotContain("gsk_test_secret", "비공개_옷_제목");
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
        when(client.recommend(input(), "input")).thenReturn(new LlmOutfitDecision(List.of()));
        assertThat(recommender(properties(true)).recommend(input())).isEmpty();
    }

    @Test
    void 예상하지_못한_코드_오류는_숨기지_않는다() {
        when(client.serializeInput(input())).thenThrow(new IllegalStateException("bug"));
        assertThatThrownBy(() -> recommender(properties(true)).recommend(input()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 참고자료가_있어도_원래_옷장을_줄이지_않고_합산_입력_길이를_지킨다() {
        var reference = new StyleReference.Context("example", "source", "https://example.com", "요약", "UNKNOWN");
        when(client.serializeInput(input())).thenReturn("full-wardrobe");
        when(references.find(input())).thenReturn(List.of(reference));
        when(client.serializeReferences(List.of(reference))).thenReturn("x".repeat(12000));
        when(client.recommend(input(), "full-wardrobe")).thenReturn(decision());

        assertThat(recommender(properties(true)).recommend(input())).contains(decision());
        verify(client).recommend(input(), "full-wardrobe");
        verify(client, never()).recommend(any(), anyString(), anyString());
    }

    @Test
    void 참고자료가_길이제한_안에_있으면_기존_입력과_함께_전달한다() {
        var reference = new StyleReference.Context("example", "source", "https://example.com", "요약", "UNKNOWN");
        when(client.serializeInput(input())).thenReturn("full-wardrobe");
        when(references.find(input())).thenReturn(List.of(reference));
        when(client.serializeReferences(List.of(reference))).thenReturn("references");
        when(client.recommend(input(), "full-wardrobe", "references")).thenReturn(decision());

        assertThat(recommender(properties(true)).recommend(input())).contains(decision());
        verify(client).recommend(input(), "full-wardrobe", "references");
    }

    @Test
    void 비활성화나_입력제한_초과이면_검색도_하지_않는다() {
        assertThat(recommender(properties(false)).recommend(input())).isEmpty();
        assertThat(recommender(new RecommendationLlmProperties(true, "model", 1, 100, 100)).recommend(input())).isEmpty();
        verifyNoInteractions(references);
    }

    private LlmOutfitRecommender recommender(RecommendationLlmProperties properties) {
        return new LlmOutfitRecommender(properties, client, validator, references);
    }

}
