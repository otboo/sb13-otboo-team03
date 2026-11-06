package com.otboo.team03.recommendation.service;

import com.otboo.team03.clothes.dto.response.ClothesAttributeWithDefDto;
import com.otboo.team03.clothes.dto.response.ClothesDto;
import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesAttribute;
import com.otboo.team03.clothes.entity.ClothesAttributeDefinition;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.clothes.repository.ClothesAttributeRepository;
import com.otboo.team03.clothes.service.ClothesService;
import com.otboo.team03.profile.dto.ProfileDto;
import com.otboo.team03.profile.exception.ProfileNotFoundException;
import com.otboo.team03.profile.service.ProfileService;
import com.otboo.team03.recommendation.algorithm.BasicOutfitAlgorithm;
import com.otboo.team03.recommendation.algorithm.AttributeSemanticAnalyzer;
import com.otboo.team03.recommendation.algorithm.ClothesThermalEstimator;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.exception.WeatherNotFoundException;
import com.otboo.team03.weather.repository.WeatherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.otboo.team03.recommendation.algorithm.ThicknessLevel.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock private ProfileService profileService;
    @Mock private ClothesRepository clothesRepository;
    @Mock private ClothesAttributeRepository clothesAttributeRepository;
    @Mock private ClothesService clothesService;
    @Mock private BasicOutfitAlgorithm algorithm;
    @Mock private WeatherRepository weatherRepository;
    @Spy private AttributeSemanticAnalyzer attributeAnalyzer = new AttributeSemanticAnalyzer();
    @Spy private ClothesThermalEstimator thermalEstimator = new ClothesThermalEstimator();
    @InjectMocks private RecommendationService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID weatherId = UUID.randomUUID();

    @Test
    void 사용자_의상과_민감도로_추천하고_응답의_이미지와_속성을_유지한다() {
        givenTemperature(10.0);
        Clothes dress = mock(Clothes.class);
        UUID clothesId = UUID.randomUUID();
        var attribute = new ClothesAttributeWithDefDto(
                UUID.randomUUID(), "색상", List.of("검정", "흰색"), "검정");
        var dto = new ClothesDto(clothesId, userId, "원피스", "https://example.com/image",
                ClothesType.DRESS, List.of(attribute));
        List<Clothes> clothes = List.of(dress);
        when(profileService.getProfile(userId)).thenReturn(profile(1));
        when(clothesRepository.findAllByOwner_Id(userId)).thenReturn(clothes);
        when(algorithm.recommend(clothes, Map.of(clothesId, UNKNOWN), 10.0, 1)).thenReturn(clothes);
        when(dress.getId()).thenReturn(clothesId);
        when(clothesService.findById(clothesId)).thenReturn(dto);

        var result = service.recommend(weatherId, userId);

        assertThat(result.weatherId()).isEqualTo(weatherId);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.clothes()).singleElement().satisfies(ootd -> {
            assertThat(ootd.clothesId()).isEqualTo(clothesId);
            assertThat(ootd.name()).isEqualTo(dto.name());
            assertThat(ootd.imageUrl()).isEqualTo(dto.imageUrl());
            assertThat(ootd.type()).isEqualTo(ClothesType.DRESS);
            assertThat(ootd.attributes()).containsExactly(attribute);
        });
        verify(weatherRepository).findById(weatherId);
        verify(algorithm).recommend(clothes, Map.of(clothesId, UNKNOWN), 10.0, 1);
    }

    @Test
    void 민감도가_없으면_중립값_3을_사용하고_빈_추천은_추가조회하지_않는다() {
        givenTemperature(22.0);
        when(profileService.getProfile(userId)).thenReturn(profile(null));
        when(clothesRepository.findAllByOwner_Id(userId)).thenReturn(List.of());
        when(algorithm.recommend(List.of(), 22.0, 3)).thenReturn(List.of());

        var result = service.recommend(weatherId, userId);

        assertThat(result.clothes()).isEmpty();
        verify(algorithm).recommend(List.of(), 22.0, 3);
        verifyNoInteractions(clothesService);
        verifyNoInteractions(clothesAttributeRepository);
    }

    @Test
    void 프로필이_없으면_예외를_전달하고_추천을_진행하지_않는다() {
        givenTemperature(10.0);
        when(profileService.getProfile(userId)).thenThrow(new ProfileNotFoundException());

        assertThatThrownBy(() -> service.recommend(weatherId, userId))
                .isInstanceOf(ProfileNotFoundException.class);

        verifyNoInteractions(clothesRepository, algorithm, clothesService);
    }

    @Test
    void 날씨가_없으면_날씨_미존재_예외를_던지고_추천을_진행하지_않는다() {
        when(weatherRepository.findById(weatherId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recommend(weatherId, userId))
                .isInstanceOf(WeatherNotFoundException.class);

        verifyNoInteractions(profileService, clothesRepository, algorithm, clothesService);
    }

    private void givenTemperature(double temperature) {
        Weather weather = mock(Weather.class);
        when(weatherRepository.findById(weatherId)).thenReturn(Optional.of(weather));
        when(weather.getTemperature()).thenReturn(temperature);
    }

    @Test
    void 의상별_속성을_일괄조회하고_해석한_두께를_알고리즘에_전달한다() {
        givenTemperature(28.0);
        when(profileService.getProfile(userId)).thenReturn(profile(3));
        Clothes light = mock(Clothes.class);
        Clothes heavy = mock(Clothes.class);
        Clothes conflicting = mock(Clothes.class);
        Clothes withoutAttributes = mock(Clothes.class);
        UUID lightId = UUID.randomUUID();
        UUID heavyId = UUID.randomUUID();
        UUID conflictingId = UUID.randomUUID();
        UUID withoutAttributesId = UUID.randomUUID();
        when(light.getId()).thenReturn(lightId);
        when(heavy.getId()).thenReturn(heavyId);
        when(conflicting.getId()).thenReturn(conflictingId);
        when(withoutAttributes.getId()).thenReturn(withoutAttributesId);
        List<Clothes> clothes = List.of(light, heavy, conflicting, withoutAttributes);
        when(clothesRepository.findAllByOwner_Id(userId)).thenReturn(clothes);
        var thickness = new ClothesAttributeDefinition("두께", List.of("얇음", "두꺼움"));
        var alias = new ClothesAttributeDefinition("두께감", List.of("도톰함"));
        var color = new ClothesAttributeDefinition("색상", List.of("검정"));
        List<UUID> ids = List.of(lightId, heavyId, conflictingId, withoutAttributesId);
        // 조회 순서가 달라도 목록 위치가 아닌 의상 ID로 속성을 연결해야 한다.
        when(clothesAttributeRepository.findAllByClothes_IdIn(ids)).thenReturn(List.of(
                new ClothesAttribute(heavy, alias, "도톰함"),
                new ClothesAttribute(conflicting, thickness, "얇음"),
                new ClothesAttribute(light, color, "검정"),
                new ClothesAttribute(light, thickness, "얇음"),
                new ClothesAttribute(conflicting, alias, "도톰함")
        ));

        service.recommend(weatherId, userId);

        verify(clothesAttributeRepository).findAllByClothes_IdIn(ids);
        verify(algorithm).recommend(clothes,
                Map.of(lightId, LIGHT, heavyId, HEAVY,
                        conflictingId, UNKNOWN, withoutAttributesId, UNKNOWN), 28.0, 3);
    }

    @Test
    void 속성이_없는_의상도_UNKNOWN으로_전달하고_민감도_기본값을_유지한다() {
        givenTemperature(20.0);
        when(profileService.getProfile(userId)).thenReturn(profile(null));
        Clothes dress = mock(Clothes.class);
        UUID clothesId = UUID.randomUUID();
        when(dress.getId()).thenReturn(clothesId);
        List<Clothes> clothes = List.of(dress);
        when(clothesRepository.findAllByOwner_Id(userId)).thenReturn(clothes);

        service.recommend(weatherId, userId);

        verify(clothesAttributeRepository).findAllByClothes_IdIn(List.of(clothesId));
        verify(algorithm).recommend(clothes, Map.of(clothesId, UNKNOWN), 20.0, 3);
    }

    private ProfileDto profile(Integer sensitivity) {
        return new ProfileDto(userId, "사용자", null, null, null, sensitivity, null);
    }
}
