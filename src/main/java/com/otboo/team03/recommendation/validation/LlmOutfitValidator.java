package com.otboo.team03.recommendation.validation;

import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.recommendation.client.dto.LlmOutfitDecision;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 소유권과 조합 구조를 검증한다. 날씨·속성 해석의 적절성은 별도 품질 평가 대상이다. */
@Component
public class LlmOutfitValidator {

    private static final int MAX_OUTFIT_SIZE = 9;

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
        return hasValidOutfit(selectedIds, wardrobe);
    }

    private boolean hasValidStructure(LlmOutfitDecision decision) {
        return decision != null && decision.clothesIds() != null && !decision.clothesIds().isEmpty()
                && decision.clothesIds().size() <= MAX_OUTFIT_SIZE
                && decision.clothesIds().stream().allMatch(id -> id != null);
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

}
