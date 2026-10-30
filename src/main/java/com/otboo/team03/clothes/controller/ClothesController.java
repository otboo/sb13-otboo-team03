package com.otboo.team03.clothes.controller;

import com.otboo.team03.clothes.dto.request.ClothesCreateRequest;
import com.otboo.team03.clothes.dto.request.ClothesUpdateRequest;
import com.otboo.team03.clothes.dto.response.ClothesDto;
import com.otboo.team03.clothes.service.ClothesService;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.global.dto.CursorResponse;
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

    /** 소유자·타입 조건으로 의상을 조회하고 다음 페이지 정보를 함께 반환한다. */
    @GetMapping
    public ResponseEntity<CursorResponse<ClothesDto>> findAll(
            @RequestParam UUID ownerId,
            @RequestParam int limit,
            @RequestParam(required = false) ClothesType typeEqual,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID idAfter) {
        return ResponseEntity.ok(clothesService.findAll(ownerId, limit, typeEqual, cursor, idAfter));
    }

    @GetMapping("/{clothesId}")
    public ResponseEntity<ClothesDto> findById(@PathVariable UUID clothesId) {
        ClothesDto clothesDto = clothesService.findById(clothesId);
        return ResponseEntity.ok(clothesDto);
    }

    @PatchMapping("/{clothesId}")
    public ResponseEntity<ClothesDto> update(
            @PathVariable UUID clothesId,
            @RequestBody @Valid ClothesUpdateRequest request,
            Authentication authentication
    ) {
        // 요청에 입력된 ID가 아니라 서버가 인증한 사용자 정보를 사용한다.
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                instanceof OtbooUserDetails userDetails)) {
            throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        UUID requesterId = userDetails.getUserDto().id();

        ClothesDto clothesDto = clothesService.update(
                clothesId,
                request,
                requesterId
        );

        return ResponseEntity.ok(clothesDto);
    }
//
//    @DeleteMapping("/{clothesId}")
//    public ResponseEntity<Void> delete(@PathVariable UUID clothesId) {
//        clothesService.delete(clothesId);
//
//        return ResponseEntity
//                .noContent().build();
//    }
}
