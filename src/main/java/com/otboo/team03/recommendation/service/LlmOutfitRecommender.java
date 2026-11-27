package com.otboo.team03.recommendation.service;

import com.otboo.team03.global.exception.llm.GroqRequestException;
import com.otboo.team03.global.exception.llm.InvalidLlmResponseException;
import com.otboo.team03.recommendation.client.GroqOutfitClient;
import com.otboo.team03.recommendation.config.RecommendationLlmProperties;
import com.otboo.team03.recommendation.client.dto.LlmOutfitDecision;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import com.otboo.team03.recommendation.validation.LlmOutfitValidator;
import com.otboo.team03.recommendation.rag.OutfitReferenceProvider;
import java.util.ArrayList;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmOutfitRecommender {

    private final RecommendationLlmProperties properties;
    private final GroqOutfitClient client;
    private final LlmOutfitValidator validator;
    private final OutfitReferenceProvider references;

    public Optional<LlmOutfitDecision> recommend(LlmRecommendationInput input) {
        if (!properties.enabled() || input.wardrobe().isEmpty()) {
            return Optional.empty();
        }
        if (input.wardrobe().size() > properties.maxClothes()) {
            log.warn("LLM 추천 입력 의상 수 초과, 기본 추천 사용: count={}", input.wardrobe().size());
            return Optional.empty();
        }
        try {
            String serializedInput = client.serializeInput(input);
            if (serializedInput.length() > properties.maxInputCharacters()) {
                log.warn("LLM 추천 입력 길이 초과, 기본 추천 사용: length={}", serializedInput.length());
                return Optional.empty();
            }
            var context = new ArrayList<>(references.find(input));
            String serializedReferences = null;
            while (!context.isEmpty()) {
                serializedReferences = client.serializeReferences(context);
                if (serializedInput.length() + serializedReferences.length() <= properties.maxInputCharacters()) break;
                context.remove(context.size() - 1);
            }
            LlmOutfitDecision decision = context.isEmpty() ? client.recommend(input, serializedInput)
                    : client.recommend(input, serializedInput, serializedReferences);
            if (!validator.isValid(decision, input)) {
                log.warn("LLM 추천 결과 검증 실패, 기본 추천 사용");
                return Optional.empty();
            }
            log.debug("LLM 의상 추천 채택: styleReferences={}, clothes={}", context.size(), decision.clothesIds().size());
            return Optional.of(decision);
        } catch (GroqRequestException exception) {
            log.warn("LLM 추천 HTTP 호출 실패, 기본 추천 사용: status={}, providerType={}, providerCode={}",
                    exception.getStatus(), exception.getErrorType(), exception.getErrorCode());
            return Optional.empty();
        } catch (RestClientException | JacksonException | InvalidLlmResponseException exception) {
            // 예외 메시지·본문에는 API 키, 제목, 속성 또는 공급자 응답이 포함될 수 있으므로 기록하지 않는다.
            log.warn("LLM 추천 호출 실패, 기본 추천 사용: errorType={}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
