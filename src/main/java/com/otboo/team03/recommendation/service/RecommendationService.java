package com.otboo.team03.recommendation.service;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.clothes.service.ClothesService;
import com.otboo.team03.feed.dto.response.OotdDto;
import com.otboo.team03.profile.service.ProfileService;
import com.otboo.team03.recommendation.algorithm.BasicOutfitAlgorithm;
import com.otboo.team03.recommendation.dto.RecommendationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private final ProfileService profileService;
    private final ClothesRepository clothesRepository;
    private final ClothesService clothesService;
    private final BasicOutfitAlgorithm algorithm;

    public RecommendationDto recommend(UUID weatherId, UUID userId) {
        // weatherId에 해당하는 날씨 정보 찾음. weather 로직이 추가되면 수정
        double temperature = 17.0;

        Integer sensitivity = profileService.getProfile(userId).temperatureSensitivity();
        int effectiveSensitivity = sensitivity != null ? sensitivity : 3;

        List<Clothes> clothes = clothesRepository.findAllByOwner_Id(userId);
        List<Clothes> recommended = algorithm.recommend(clothes, temperature, effectiveSensitivity);

        List<OotdDto> ootds = recommended.stream()
                .map(c -> clothesService.findById(c.getId()))
                .map(OotdDto::fromClothesDto)
                .toList();

        return new RecommendationDto(weatherId, userId, ootds);
    }
}
