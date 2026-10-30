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

    @Transactional(readOnly = true)
    public List<ClothesAttributeDefDto> findAll() {
        // 공유 정의와 선택지를 반환한다. 특정 의상의 선택값은 ClothesService에서 조회한다.
        // 정의가 없으면 빈 목록을 반환하며, 조회 과정에서 정의를 새로 만들지 않는다.
        return repository.findAll().stream()
                .map(ClothesAttributeDefDto::from)
                .toList();
    }
}
