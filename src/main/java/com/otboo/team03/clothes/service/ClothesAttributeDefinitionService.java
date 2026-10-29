package com.otboo.team03.clothes.service;

import com.otboo.team03.clothes.dto.response.ClothesAttributeDefDto;
import com.otboo.team03.clothes.repository.ClothesAttributeDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClothesAttributeDefinitionService {

    private final ClothesAttributeDefinitionRepository repository;

    @Transactional(readOnly = true)
    public List<ClothesAttributeDefDto> findAll() {
        // 공유 정의와 선택지를 반환한다. 특정 의상의 선택값은 ClothesService에서 조회한다.
        // 정의가 없으면 빈 목록을 반환하며, 조회 과정에서 정의를 새로 만들지 않는다.
        return repository.findAll().stream()
                .map(ClothesAttributeDefDto::from)
                .toList();
    }
}
