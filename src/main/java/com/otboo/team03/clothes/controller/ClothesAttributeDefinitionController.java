package com.otboo.team03.clothes.controller;

import com.otboo.team03.clothes.dto.response.ClothesAttributeDefDto;
import com.otboo.team03.clothes.service.ClothesAttributeDefinitionService;
import lombok.RequiredArgsConstructor;
import com.otboo.team03.clothes.dto.request.ClothesAttributeDefCreateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.otboo.team03.clothes.dto.request.ClothesAttributeDefUpdateRequest;
import org.springframework.web.bind.annotation.PatchMapping;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 여러 의상이 공유하는 속성 정의의 등록·조회·수정·삭제 진입점.
 * 의상 자체의 생성·조회는 ClothesController가 담당한다.
 * 등록·수정·삭제는 관리자만, 조회는 인증된 사용자가 호출한다. 권한은 SecurityConfig에서 검사한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clothes/attribute-defs")
public class ClothesAttributeDefinitionController {

    private final ClothesAttributeDefinitionService service;

    /** 관리자가 정의를 등록한다. 권한은 SecurityConfig에서 검사하고 입력은 @Valid로 검증한다. */
    @PostMapping
    public ResponseEntity<ClothesAttributeDefDto> create(
            @Valid @RequestBody ClothesAttributeDefCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    /**
     * 이름·선택지를 부분 수정한다. 생략/null은 유지하며 선택지 목록은 전체 교체한다.
     * 의상에서 사용 중인 선택값 제거는 거부한다. ID와 최초 생성 시각은 유지한다.
     */
    @PatchMapping("/{definitionId}")
    public ResponseEntity<ClothesAttributeDefDto> update(@PathVariable UUID definitionId,
            @Valid @RequestBody ClothesAttributeDefUpdateRequest request) {
        return ResponseEntity.ok(service.update(definitionId, request));
    }

    /**
     * 의상에서 사용 중인 정의는 400으로 거부하고, 미사용 정의만 삭제하여 204를 반환한다.
     * 의상 삭제와 달리 연결된 속성값을 자동 제거하는 API가 아니다.
     */
    @DeleteMapping("/{definitionId}")
    public ResponseEntity<Void> delete(@PathVariable UUID definitionId) {
        service.delete(definitionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 의상 등록 화면에 정의 ID, 이름, 선택지, 정의 생성 시각을 제공한다.
     * 클라이언트는 여기서 받은 id를 의상 생성 요청의 definitionId로 전달한다.
     * 선택한 값(value)은 이 응답에 없으며 의상별 속성에서 관리한다.
     * sortBy(name/createdAt)와 sortDirection(ASCENDING/DESCENDING)은 필수다.
     * keywordLike는 정의 이름의 대소문자 구분 없는 부분 검색이며, 응답은 페이지 객체가 아닌 배열이다.
     * 조건에 맞는 정의가 없으면 200 OK와 빈 배열을 반환한다.
     */
    @GetMapping
    public ResponseEntity<List<ClothesAttributeDefDto>> findAll(
            @RequestParam String sortBy,
            @RequestParam String sortDirection,
            @RequestParam(required = false) String keywordLike) {
        return ResponseEntity.ok(service.findAll(sortBy, sortDirection, keywordLike));
    }
}
