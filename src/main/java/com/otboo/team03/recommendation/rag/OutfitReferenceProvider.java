package com.otboo.team03.recommendation.rag;

import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import java.util.List;

@FunctionalInterface
public interface OutfitReferenceProvider {
    List<StyleReference.Context> find(LlmRecommendationInput input);

    /** Prepare reusable references without waiting or calling the recommendation LLM. */
    default void prepare(LlmRecommendationInput input) { }
}
