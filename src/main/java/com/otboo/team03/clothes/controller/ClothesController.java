package com.otboo.team03.clothes.controller;

import com.otboo.team03.clothes.dto.request.ClothesCreateRequest;
import com.otboo.team03.clothes.dto.request.ClothesUpdateRequest;
import com.otboo.team03.clothes.dto.response.ClothesDto;
import com.otboo.team03.clothes.service.ClothesService;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.global.security.OtbooUserDetails;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clothes")
public class ClothesController {
    private final ClothesService clothesService;

    @PostMapping
    public ResponseEntity<ClothesDto > create(@RequestBody @Valid ClothesCreateRequest request, Authentication authentication){
        //Spring Security가 현재 인증 정보를 전달
        if(authentication == null || !authentication.isAuthenticated()
        || !(authentication.getPrincipal()  instanceof  OtbooUserDetails userDetails)){
            //getPrincipal은 인증된 사용자의 객체를 꺼내는 것과 userDetails에서 서버가 인즈한 사용자 id를 가져옴
            throw  new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        if (!userDetails.getUserDto().id().equals(request.ownerId())) {
            throw new OtbooException(ErrorCode.ACCESS_DENIED);
        }

        ClothesDto clothesDto =  clothesService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(clothesDto);
    }

    @GetMapping
    public ResponseEntity<List<ClothesDto>> findAllByOwnerId(@RequestParam UUID ownerId) {
        // /api/clothes?ownerId=... 의 쿼리 파라미터를 받아 사용자별 목록을 조회한다.
        List<ClothesDto> clothesResponses = clothesService.findAllByOwnerId(ownerId);
        // 결과가 없으면 200 OK와 빈 JSON 배열([])을 반환한다.
        return ResponseEntity.ok(clothesResponses);
    }

    @GetMapping("/{clothesId}")
    public ResponseEntity<ClothesDto> findById(@PathVariable UUID clothesId) {
        ClothesDto clothesDto = clothesService.findById(clothesId);
        return ResponseEntity.ok(clothesDto);
    }

    @PatchMapping("/{clothesId}")
    public ResponseEntity<ClothesDto> update(@PathVariable UUID clothesId,
                                            @RequestBody @Valid ClothesUpdateRequest request) {
        // URL의 ID와 JSON 수정 요청을 Service에 전달한다.
        ClothesDto clothesDto = clothesService.update(clothesId, request);
        return ResponseEntity.ok(clothesDto);
    }

    @DeleteMapping("/{clothesId}")
    public ResponseEntity<Void> delete(@PathVariable UUID clothesId) {
        clothesService.delete(clothesId);

        return ResponseEntity
                .noContent().build();
    }
}
