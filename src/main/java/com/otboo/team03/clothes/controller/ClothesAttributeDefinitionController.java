package com.otboo.team03.clothes.controller;

import com.otboo.team03.clothes.dto.response.ClothesAttributeDefDto;
import com.otboo.team03.clothes.service.ClothesAttributeDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 여러 의상이 공유하는 속성 정의의 조회 진입점.
 * 의상 자체의 생성·조회는 ClothesController가 담당한다.
 * 현재 전역 보안 설정에 따라 인증된 사용자가 호출할 수 있다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clothes/attribute-defs")
public class ClothesAttributeDefinitionController {

    private final ClothesAttributeDefinitionService service;

    /**
     * 의상 등록 화면에 정의 ID, 이름, 선택지, 정의 생성 시각을 제공한다.
     * 클라이언트는 여기서 받은 id를 의상 생성 요청의 definitionId로 전달한다.
     * 선택한 값(value)은 이 응답에 없으며 의상별 속성에서 관리한다.
     * 전체 목록을 반환하고, 정의가 없으면 200 OK와 빈 배열을 반환한다.
     */
    @GetMapping
    public ResponseEntity<List<ClothesAttributeDefDto>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }
}
