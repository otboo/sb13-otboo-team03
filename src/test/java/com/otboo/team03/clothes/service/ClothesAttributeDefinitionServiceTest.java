package com.otboo.team03.clothes.service;

import com.otboo.team03.clothes.dto.request.ClothesAttributeDefCreateRequest;
import com.otboo.team03.clothes.entity.ClothesAttributeDefinition;
import com.otboo.team03.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.team03.global.exception.OtbooException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DB를 대체한 서비스 단위 테스트. 내부 호출 입력 검증과 삭제 예외 변환을 확인한다.
 * 동시 참조 예외는 모의한 것이며 실제 동시 트랜잭션 경합을 재현하는 테스트는 아니다.
 */
class ClothesAttributeDefinitionServiceTest {
    private final ClothesAttributeDefinitionRepository repository = mock(ClothesAttributeDefinitionRepository.class);
    private final com.otboo.team03.clothes.repository.ClothesAttributeRepository attributes =
            mock(com.otboo.team03.clothes.repository.ClothesAttributeRepository.class);
    private final ClothesAttributeDefinitionService service = new ClothesAttributeDefinitionService(repository, attributes);

    @Test
    void invalidUpdateRequestsPreserveDefinition() {
        UUID id = UUID.randomUUID();
        var definition = new ClothesAttributeDefinition("색상", List.of("파랑", "빨강"));
        when(repository.findForChange(id)).thenReturn(Optional.of(definition));
        var requests = Arrays.asList(
                null,
                new com.otboo.team03.clothes.dto.request.ClothesAttributeDefUpdateRequest(" ", null),
                new com.otboo.team03.clothes.dto.request.ClothesAttributeDefUpdateRequest("a".repeat(256), null),
                new com.otboo.team03.clothes.dto.request.ClothesAttributeDefUpdateRequest("변경", List.of()),
                new com.otboo.team03.clothes.dto.request.ClothesAttributeDefUpdateRequest("변경", Arrays.asList((String) null)),
                new com.otboo.team03.clothes.dto.request.ClothesAttributeDefUpdateRequest("변경", List.of(" ")),
                new com.otboo.team03.clothes.dto.request.ClothesAttributeDefUpdateRequest("변경", List.of("a".repeat(256))));
        for (var request : requests) {
            assertThatThrownBy(() -> service.update(id, request)).isInstanceOfSatisfying(OtbooException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(com.otboo.team03.global.exception.ErrorCode.INVALID_INPUT_VALUE));
            assertThat(definition.getName()).isEqualTo("색상");
            assertThat(definition.getSelectableValues()).containsExactly("파랑", "빨강");
        }
        verifyNoInteractions(attributes);
        verify(repository, never()).save(any());
    }

    @Test
    void emptyKeywordQueriesAllDefinitionsWithSort() {
        var sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "name")
                .and(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "id"));
        when(repository.findAll(sort)).thenReturn(List.of());
        assertThat(service.findAll("name", "ASCENDING", "")).isEmpty();
        verify(repository).findAll(sort);
        verify(repository, never()).findByNameContainingIgnoreCase(anyString(), any());
    }

    @Test
    void otherForeignKeyViolationIsNotReportedAsDefinitionInUse() {
        var id = UUID.randomUUID();
        when(repository.findForChange(id)).thenReturn(Optional.of(new ClothesAttributeDefinition("색상", List.of("파랑"))));
        var violation = new org.hibernate.exception.ConstraintViolationException(
                "other constraint", new java.sql.SQLException("foreign key", "23503"), "unrelated_constraint");
        var failure = new org.springframework.dao.DataIntegrityViolationException("unrelated", violation);
        doThrow(failure).when(repository).flush();
        assertThatThrownBy(() -> service.delete(id)).isSameAs(failure);
    }

    @Test
    void concurrentReferenceConstraintIsReportedAsInUse() {
        var id = UUID.randomUUID();
        when(repository.findForChange(id)).thenReturn(Optional.of(new ClothesAttributeDefinition("색상", List.of("파랑"))));
        var violation = new org.hibernate.exception.ConstraintViolationException(
                "referenced", new java.sql.SQLException("foreign key", "23503"),
                "fk_clothes_attribute_definitions_to_clothes_attribute");
        doThrow(new org.springframework.dao.DataIntegrityViolationException("delete", violation))
                .when(repository).flush();
        assertThatThrownBy(() -> service.delete(id)).isInstanceOfSatisfying(OtbooException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(com.otboo.team03.global.exception.ErrorCode.ATTRIBUTE_DEFINITION_IN_USE));
    }

    @Test
    void unrelatedDatabaseFailureIsNotHidden() {
        var id = UUID.randomUUID();
        when(repository.findForChange(id)).thenReturn(Optional.of(new ClothesAttributeDefinition("색상", List.of("파랑"))));
        var failure = new org.springframework.dao.DataIntegrityViolationException("unrelated");
        doThrow(failure).when(repository).flush();
        assertThatThrownBy(() -> service.delete(id)).isSameAs(failure);
    }

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
