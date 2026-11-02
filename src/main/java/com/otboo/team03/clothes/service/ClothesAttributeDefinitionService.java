package com.otboo.team03.clothes.service;

import com.otboo.team03.clothes.dto.response.ClothesAttributeDefDto;
import com.otboo.team03.clothes.dto.request.ClothesAttributeDefCreateRequest;
import com.otboo.team03.clothes.dto.request.ClothesAttributeDefUpdateRequest;
import com.otboo.team03.clothes.entity.ClothesAttributeDefinition;
import com.otboo.team03.clothes.exception.ClothesAttributeDefinitionNotFoundException;
import com.otboo.team03.clothes.repository.ClothesAttributeRepository;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.clothes.repository.ClothesAttributeDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 여러 의상이 공유하는 속성 정의의 수명주기를 관리한다.
 * 정의 삭제는 참조 중이면 거부하고, 선택지 수정은 사용 중인 값 제거를 거부한다.
 * 기존 의상의 선택값을 다른 값으로 자동 치환하거나 의상·피드 자체를 삭제하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ClothesAttributeDefinitionService {

    private final ClothesAttributeDefinitionRepository repository;
    private final ClothesAttributeRepository clothesAttributeRepository;

    /** 정의만 저장한다. 의상 및 의상별 선택값은 생성하지 않는다. */
    @Transactional
    public ClothesAttributeDefDto create(ClothesAttributeDefCreateRequest request) {
        // HTTP 검증을 거치지 않는 내부 호출에서도 DB와 의상 속성값의 길이 제한을 보장한다.
        if (request == null || request.name() == null || request.name().isBlank()
                || request.name().length() > 255 || request.selectableValues() == null
                || request.selectableValues().isEmpty()
                || request.selectableValues().stream().anyMatch(value ->
                        value == null || value.isBlank() || value.length() > 255)) {
            throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
        }
        var definition = new ClothesAttributeDefinition(request.name(), request.selectableValues());
        // ID와 createdAt은 JPA 저장 과정에서 설정되며, 저장 결과를 응답으로 변환한다.
        return ClothesAttributeDefDto.from(repository.save(definition));
    }

    /** 이름 부분 일치 검색과 명세의 정렬 조건을 DB 조회에 적용한다. */
    @Transactional(readOnly = true)
    public List<ClothesAttributeDefDto> findAll(String sortBy, String sortDirection, String keywordLike) {
        // 외부 입력을 임의의 엔티티 필드명으로 사용하지 않도록 허용값만 통과시킨다.
        if (!("createdAt".equals(sortBy) || "name".equals(sortBy))
                || !("ASCENDING".equals(sortDirection) || "DESCENDING".equals(sortDirection))) {
            throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Sort.Direction direction = "ASCENDING".equals(sortDirection)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        // 같은 정렬값도 일정한 순서로 반환하도록 ID를 보조 기준으로 사용한다.
        Sort sort = Sort.by(direction, sortBy).and(Sort.by(direction, "id"));
        List<ClothesAttributeDefinition> definitions = keywordLike == null || keywordLike.isEmpty()
                ? repository.findAll(sort)
                : repository.findByNameContainingIgnoreCase(keywordLike, sort);
        return definitions.stream().map(ClothesAttributeDefDto::from).toList();
    }
    /** 공유 정의를 수정하되 기존 의상에 사용 중인 선택값은 보존한다. */
    @Transactional
    public ClothesAttributeDefDto update(UUID definitionId, ClothesAttributeDefUpdateRequest request) {
        var definition = repository.findById(definitionId)
                .orElseThrow(ClothesAttributeDefinitionNotFoundException::new);
        if (request == null || (request.name() != null
                && (request.name().isBlank() || request.name().length() > 255))
                || (request.selectableValues() != null && (request.selectableValues().isEmpty()
                || request.selectableValues().stream().anyMatch(value ->
                        value == null || value.isBlank() || value.length() > 255)))) {
            throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.selectableValues() != null) {
            // 새 목록에 빠진 선택값만 검사한다. 추가와 순서 변경은 기존 의상에 영향을 주지 않는다.
            var removed = definition.getSelectableValues().stream()
                    .filter(value -> !request.selectableValues().contains(value)).toList();
            if (!removed.isEmpty()
                    && clothesAttributeRepository.existsByDefinition_IdAndValueIn(definitionId, removed)) {
                throw new OtbooException(ErrorCode.ATTRIBUTE_VALUE_IN_USE);
            }
        }
        // 검증이 끝난 뒤 변경한다. ID와 최초 생성 시각은 유지된다.
        // 정의와 함께 의상을 조립해 조회하는 다른 도메인에도 변경된 이름·선택지가 반영된다.
        // 이 사용 여부 조회만으로 동시 의상 등록/수정과의 경합까지 직렬화하지는 않는다.
        definition.update(request.name(), request.selectableValues());
        return ClothesAttributeDefDto.from(definition);
    }

    /**
     * 미사용 정의만 삭제한다. 존재하지 않으면 404, 사용 중이면 400으로 응답한다.
     * 사전 조회 후 발생한 참조는 DB 외래 키가 최종 보호하며 flush에서 그 위반을 변환한다.
     */
    @Transactional
    public void delete(UUID definitionId) {
        ClothesAttributeDefinition definition = repository.findById(definitionId)
                .orElseThrow(ClothesAttributeDefinitionNotFoundException::new);

        // 사용 중이면 아무 데이터도 삭제하지 않는다.
        if (clothesAttributeRepository.existsByDefinition_Id(definitionId)) {
            throw new OtbooException(ErrorCode.ATTRIBUTE_DEFINITION_IN_USE);
        }

        try {
            repository.delete(definition);
            // 커밋 전 SQL을 실행해 사용 여부 확인 이후 생긴 동시 참조도 여기서 처리한다.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            // 해당 외래 키 위반만 도메인 오류로 바꾼다. 다른 DB 오류를 사용 중 오류로 숨기지 않는다.
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                        && "fk_clothes_attribute_definitions_to_clothes_attribute".equals(violation.getConstraintName())) {
                    throw new OtbooException(ErrorCode.ATTRIBUTE_DEFINITION_IN_USE);
                }
            }
            throw ex;
        }
    }
}
