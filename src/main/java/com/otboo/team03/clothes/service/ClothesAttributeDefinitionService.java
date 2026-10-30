package com.otboo.team03.clothes.service;

import com.otboo.team03.clothes.dto.response.ClothesAttributeDefDto;
import com.otboo.team03.clothes.dto.request.ClothesAttributeDefCreateRequest;
import com.otboo.team03.clothes.entity.ClothesAttributeDefinition;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.clothes.repository.ClothesAttributeDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import org.springframework.data.domain.Sort;

@Service
@RequiredArgsConstructor
public class ClothesAttributeDefinitionService {

    private final ClothesAttributeDefinitionRepository repository;

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
}
