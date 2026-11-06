package com.otboo.team03.recommendation.service;

import com.otboo.team03.clothes.dto.response.ClothesAttributeWithDefDto;
import com.otboo.team03.clothes.dto.response.ClothesDto;
import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.clothes.service.ClothesService;
import com.otboo.team03.profile.dto.ProfileDto;
import com.otboo.team03.profile.exception.ProfileNotFoundException;
import com.otboo.team03.profile.service.ProfileService;
import com.otboo.team03.recommendation.algorithm.BasicOutfitAlgorithm;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.exception.WeatherNotFoundException;
import com.otboo.team03.weather.repository.WeatherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock private ProfileService profileService;
    @Mock private ClothesRepository clothesRepository;
    @Mock private ClothesService clothesService;
    @Mock private BasicOutfitAlgorithm algorithm;
    @Mock private WeatherRepository weatherRepository;
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
        when(algorithm.recommend(clothes, 10.0, 1)).thenReturn(clothes);
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
        verify(algorithm).recommend(clothes, 10.0, 1);
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

    private ProfileDto profile(Integer sensitivity) {
        return new ProfileDto(userId, "사용자", null, null, null, sensitivity, null);
    }
}
