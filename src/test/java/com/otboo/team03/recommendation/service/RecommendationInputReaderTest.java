package com.otboo.team03.recommendation.service;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesAttribute;
import com.otboo.team03.clothes.entity.ClothesAttributeDefinition;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.clothes.repository.ClothesAttributeRepository;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.profile.service.ProfileService;
import com.otboo.team03.recommendation.algorithm.AttributeSemanticAnalyzer;
import com.otboo.team03.recommendation.algorithm.ClothesAttributeEstimator;
import com.otboo.team03.recommendation.algorithm.SleeveLengthAnalyzer;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import com.otboo.team03.weather.repository.WeatherRepository;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationInputReaderTest {
    @Mock private ClothesRepository clothesRepository;
    @Mock private ClothesAttributeRepository attributesRepository;
    @Mock private WeatherRepository weatherRepository;
    @Mock private ProfileService profileService;
    @Mock private AttributeSemanticAnalyzer attributeAnalyzer;
    @Mock private ClothesAttributeEstimator attributeEstimator;
    @Mock private SleeveLengthAnalyzer sleeveAnalyzer;
    @InjectMocks private RecommendationInputReader reader;

    @Test void 의상_81개이면_속성_조회와_DTO_생성_전에_종료한다() {
        UUID ownerId = UUID.randomUUID();
        Clothes untouched = mock(Clothes.class);
        when(clothesRepository.findPage(ownerId, null, null, null, 81))
                .thenReturn(Collections.nCopies(81, untouched));

        assertThat(reader.readForStyleReferences(ownerId, 80)).isEmpty();

        verify(clothesRepository).findPage(ownerId, null, null, null, 81);
        verifyNoMoreInteractions(clothesRepository);
        verifyNoInteractions(untouched, attributesRepository, weatherRepository, profileService);
    }

    @Test void 의상_80개까지는_전체_속성을_한번에_조회하여_문맥을_만든다() {
        UUID ownerId = UUID.randomUUID();
        var clothes = IntStream.range(0, 80).mapToObj(index -> {
            var item = new Clothes("원피스 " + index, ClothesType.DRESS, null, null);
            ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
            return item;
        }).toList();
        var ids = clothes.stream().map(Clothes::getId).toList();
        var definition = new ClothesAttributeDefinition("색상", List.of("분홍"));
        when(clothesRepository.findPage(ownerId, null, null, null, 81)).thenReturn(clothes);
        when(attributesRepository.findAllByClothes_IdIn(ids))
                .thenReturn(List.of(new ClothesAttribute(clothes.get(0), definition, "분홍")));

        var input = reader.readForStyleReferences(ownerId, 80).orElseThrow();

        assertThat(input.wardrobe()).extracting(LlmRecommendationInput.Item::clothesId)
                .containsExactlyElementsOf(ids);
        assertThat(input.wardrobe().get(0).attributes())
                .containsExactly(new LlmRecommendationInput.Attribute("색상", "분홍"));
        verify(attributesRepository).findAllByClothes_IdIn(ids);
        verifyNoMoreInteractions(attributesRepository);
        verifyNoInteractions(weatherRepository, profileService);
    }
}
