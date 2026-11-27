package com.otboo.team03.recommendation.service;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.clothes.service.ClothesService;
import com.otboo.team03.feed.dto.response.OotdDto;
import com.otboo.team03.recommendation.algorithm.BasicOutfitAlgorithm;
import com.otboo.team03.recommendation.dto.RecommendationDto;
import com.otboo.team03.recommendation.dto.RecommendationSnapshot;
import com.otboo.team03.recommendation.repository.RedisRecommendationHistoryRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final List<ClothesType> RESPONSE_ORDER = List.of(ClothesType.TOP, ClothesType.BOTTOM,
            ClothesType.DRESS, ClothesType.OUTER, ClothesType.SHOES, ClothesType.SOCKS,
            ClothesType.BAG, ClothesType.HAT, ClothesType.ACCESSORY, ClothesType.SCARF);

    private final RecommendationInputReader inputReader;
    private final BasicOutfitAlgorithm algorithm;
    private final LlmOutfitRecommender llmRecommender;
    private final ClothesService clothesService;
    private final RedisRecommendationHistoryRepository historyRepository;

    // 입력 조회의 짧은 트랜잭션이 끝난 뒤 외부 API를 호출한다.
    public RecommendationDto recommend(UUID weatherId, UUID userId) {
        RecommendationSnapshot snapshot = inputReader.read(weatherId, userId);
        List<Set<UUID>> recentOutfits = historyRepository.findRecent(userId, weatherId);
        Set<UUID> previousOutfitIds = recentOutfits.isEmpty() ? Set.of() : recentOutfits.get(0);
        List<Clothes> recommended = chooseOutfit(snapshot, previousOutfitIds, recentOutfits);

        List<OotdDto> ootds = recommended.stream()
                .map(item -> clothesService.toClothesDto(item,
                        snapshot.attributesByClothesId().getOrDefault(item.getId(), List.of())))
                .map(OotdDto::fromClothesDto)
                .toList();
        if (recommended.isEmpty()) {
            historyRepository.delete(userId, weatherId);
        } else {
            Set<UUID> ids = recommended.stream().map(Clothes::getId).collect(Collectors.toUnmodifiableSet());
            historyRepository.save(userId, weatherId, ids);
        }
        return new RecommendationDto(weatherId, userId, ootds);
    }

    private List<Clothes> chooseOutfit(RecommendationSnapshot snapshot, Set<UUID> previousOutfitIds, List<Set<UUID>> recentOutfits) {
        List<Clothes> fallback = algorithm.recommend(snapshot.clothes(), snapshot.thicknessByClothesId(),
                snapshot.sleeveByClothesId(), snapshot.weather().temperature(), snapshot.sensitivity(), previousOutfitIds);
        if (fallback.isEmpty()) {
            return fallback;
        }
        return llmRecommender.recommend(snapshot.llmInput(previousOutfitIds, recentOutfits))
                .map(decision -> selectOwnedClothes(snapshot, Set.copyOf(decision.clothesIds())))
                .orElse(fallback);
    }

    private List<Clothes> selectOwnedClothes(RecommendationSnapshot snapshot, Set<UUID> selectedIds) {
        return snapshot.clothes().stream()
                .filter(item -> selectedIds.contains(item.getId()))
                .sorted(Comparator.comparingInt(item -> RESPONSE_ORDER.indexOf(item.getType())))
                .toList();
    }
}
