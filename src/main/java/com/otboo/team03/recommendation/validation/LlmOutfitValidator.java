package com.otboo.team03.recommendation.validation;

import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.recommendation.client.dto.LlmOutfitDecision;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 사전의 속성 점수로 제한하지 않고 소유권·조합 형태·인용 근거를 검증한다. */
@Component
public class LlmOutfitValidator {

    private static final int MAX_OUTFIT_SIZE = 9;
    private static final int MAX_FEATURES_PER_ITEM = 8;
    private static final int MAX_REASON_LENGTH = 1000;
    private static final int MAX_FEATURE_TEXT_LENGTH = 255;
    private static final int MAX_EVIDENCE_LENGTH = 512;

    public boolean isValid(LlmOutfitDecision decision, LlmRecommendationInput input) {
        if (!hasValidStructure(decision)) {
            return false;
        }
        Set<UUID> selectedIds = new HashSet<>(decision.clothesIds());
        if (selectedIds.size() != decision.clothesIds().size()) {
            return false;
        }
        Map<UUID, LlmRecommendationInput.Item> wardrobe = input.wardrobe().stream()
                .collect(Collectors.toMap(LlmRecommendationInput.Item::clothesId, Function.identity()));
        return hasValidOutfit(selectedIds, wardrobe)
                && hasValidAnalyses(decision.analyses(), selectedIds, wardrobe);
    }

    private boolean hasValidStructure(LlmOutfitDecision decision) {
        return decision != null && decision.clothesIds() != null && !decision.clothesIds().isEmpty()
                && decision.clothesIds().size() <= MAX_OUTFIT_SIZE && decision.analyses() != null
                && decision.analyses().size() == decision.clothesIds().size()
                && validText(decision.reason(), MAX_REASON_LENGTH);
    }

    private boolean hasValidOutfit(Set<UUID> selectedIds, Map<UUID, LlmRecommendationInput.Item> wardrobe) {
        Set<ClothesType> types = EnumSet.noneOf(ClothesType.class);
        for (UUID id : selectedIds) {
            LlmRecommendationInput.Item item = wardrobe.get(id);
            if (item == null || item.type() == null
                    || item.type() == ClothesType.UNDERWEAR || item.type() == ClothesType.ETC
                    || !types.add(item.type())) {
                return false;
            }
        }
        boolean separates = types.contains(ClothesType.TOP) && types.contains(ClothesType.BOTTOM)
                && !types.contains(ClothesType.DRESS);
        boolean dress = types.contains(ClothesType.DRESS) && !types.contains(ClothesType.TOP)
                && !types.contains(ClothesType.BOTTOM);
        boolean socksWithoutShoes = types.contains(ClothesType.SOCKS) && !types.contains(ClothesType.SHOES);
        return (separates || dress) && !socksWithoutShoes;
    }

    private boolean hasValidAnalyses(List<LlmOutfitDecision.Analysis> analyses, Set<UUID> selectedIds,
                                     Map<UUID, LlmRecommendationInput.Item> wardrobe) {
        Set<UUID> analysedIds = new HashSet<>();
        for (LlmOutfitDecision.Analysis analysis : analyses) {
            if (analysis == null || !selectedIds.contains(analysis.clothesId())
                    || !analysedIds.add(analysis.clothesId()) || analysis.features() == null
                    || analysis.features().size() > MAX_FEATURES_PER_ITEM) {
                return false;
            }
            LlmRecommendationInput.Item item = wardrobe.get(analysis.clothesId());
            for (LlmOutfitDecision.Feature feature : analysis.features()) {
                if (!validFeature(feature, item)) {
                    return false;
                }
            }
        }
        return analysedIds.equals(selectedIds);
    }

    private boolean validFeature(LlmOutfitDecision.Feature feature, LlmRecommendationInput.Item item) {
        if (feature == null || !validText(feature.name(), MAX_FEATURE_TEXT_LENGTH)
                || !validText(feature.value(), MAX_FEATURE_TEXT_LENGTH)
                || !validText(feature.evidence(), MAX_EVIDENCE_LENGTH) || feature.source() == null) {
            return false;
        }
        return switch (feature.source()) {
            case TITLE -> item.name() != null && item.name().contains(feature.evidence());
            case ATTRIBUTE -> item.attributes().stream().anyMatch(attribute ->
                    attribute.value() != null && attribute.value().contains(feature.evidence()));
        };
    }

    private boolean validText(String text, int maxLength) {
        return text != null && !text.isBlank() && text.length() <= maxLength;
    }
}
