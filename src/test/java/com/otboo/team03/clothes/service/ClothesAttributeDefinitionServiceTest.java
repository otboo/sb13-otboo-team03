package com.otboo.team03.clothes.service;

import com.otboo.team03.clothes.dto.request.ClothesAttributeDefCreateRequest;
import com.otboo.team03.clothes.entity.ClothesAttributeDefinition;
import com.otboo.team03.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.team03.global.exception.OtbooException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClothesAttributeDefinitionServiceTest {
    private final ClothesAttributeDefinitionRepository repository = mock(ClothesAttributeDefinitionRepository.class);
    private final ClothesAttributeDefinitionService service = new ClothesAttributeDefinitionService(repository);

    @Test
    void invalidInternalRequestsDoNotSave() {
        // HTTP의 @Valid를 거치지 않는 호출도 잘못된 정의를 저장하지 않아야 한다.
        var requests = Arrays.asList(
                null,
                new ClothesAttributeDefCreateRequest(null, List.of("면")),
                new ClothesAttributeDefCreateRequest(" ", List.of("면")),
                new ClothesAttributeDefCreateRequest("a".repeat(256), List.of("면")),
                new ClothesAttributeDefCreateRequest("소재", null),
                new ClothesAttributeDefCreateRequest("소재", List.of()),
                new ClothesAttributeDefCreateRequest("소재", Arrays.asList((String) null)),
                new ClothesAttributeDefCreateRequest("소재", List.of(" ")),
                new ClothesAttributeDefCreateRequest("소재", List.of("a".repeat(256))));
        for (var request : requests) {
            assertThatThrownBy(() -> service.create(request)).isInstanceOf(OtbooException.class);
        }
        verifyNoInteractions(repository);
    }

    @Test
    void listReturnsDefinitionDataAndEmptyList() {
        var definition = new ClothesAttributeDefinition("소재", List.of("면", "울"));
        when(repository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(definition), List.of());
        var result = service.findAll("name", "ASCENDING", null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("소재");
        assertThat(result.get(0).selectableValues()).containsExactly("면", "울");
        assertThat(service.findAll("name", "ASCENDING", null)).isEmpty();
    }
}
