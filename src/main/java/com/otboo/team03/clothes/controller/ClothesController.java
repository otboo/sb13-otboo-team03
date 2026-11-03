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

/**
 * 사용자 소유 의상의 생성·조회·수정·삭제 HTTP 진입점.
 * 생성·수정·삭제는 본인 소유만 허용하고, 조회는 인증 후 지정한 소유자의 의상을 반환한다.
 * 이미지 업로드는 아직 연결되지 않아 생성·수정은 현재 JSON 본문을 받는다.
 */
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
            // principal 타입을 확인한 후 서버가 인증한 사용자 ID를 사용한다.
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
    /**
     * 서버가 인증한 요청자 ID를 전달하고 삭제 성공 시 204를 반환한다.
     * 삭제된 의상은 기존 피드의 의상 목록에서도 빠지며 피드 본문은 유지된다.
     */
    @DeleteMapping("/{clothesId}")
    public ResponseEntity<Void> delete(@PathVariable UUID clothesId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof OtbooUserDetails userDetails)) {
            throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        clothesService.delete(clothesId, userDetails.getUserDto().id());
        return ResponseEntity.noContent().build();
    }
}
