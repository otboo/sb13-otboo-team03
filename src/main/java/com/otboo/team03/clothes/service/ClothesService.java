package com.otboo.team03.clothes.service;

import com.otboo.team03.clothes.dto.request.ClothesCreateRequest;
import com.otboo.team03.clothes.dto.request.ClothesUpdateRequest;
import com.otboo.team03.clothes.dto.response.ClothesDto;
import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClothesService {
    private final ClothesRepository clothesRepository;

    @Transactional
    public ClothesDto create(ClothesCreateRequest request){
        Clothes clothes = new Clothes(request.name(),request.type(),null,request.ownerId());
        Clothes savedClothes = clothesRepository.save(clothes);

        return new ClothesDto(savedClothes.getId(),
                savedClothes.getOwnerId(),
                savedClothes.getName(),
                savedClothes.getImageUrl(),
                savedClothes.getType(),
                List.of());
    }

    @Transactional(readOnly = true)
    public ClothesDto findById(UUID clothesId) {
        Optional<Clothes> clothesOptional = clothesRepository.findById(clothesId);
        // 조회 결과가 없으면 DTO를 만들지 않고 예외 처리로 넘어간다.
        if (clothesOptional.isEmpty()) {
            throw new OtbooException(ErrorCode.NOT_FOUND_CLOTHES);
        }

        Clothes clothes = clothesOptional.get();
        return new ClothesDto(clothes.getId(),
                clothes.getOwnerId(),
                clothes.getName(),
                clothes.getImageUrl(),
                clothes.getType(),
                List.of());
    }
    @Transactional
    public ClothesDto update(UUID clothesId, ClothesUpdateRequest request) {
        Optional<Clothes> clothesOptional = clothesRepository.findById(clothesId);
        if (clothesOptional.isEmpty()) {
            throw new OtbooException(ErrorCode.NOT_FOUND_CLOTHES);
        }

        Clothes clothes = clothesOptional.get();
        // 트랜잭션 안에서 조회한 엔티티는 변경 감지로 수정 내용이 DB에 반영된다.
        clothes.update(request.name(), request.type());

        // 속성 수정은 아직 구현하지 않았으므로 기본 정보만 반환한다.
        return new ClothesDto(clothes.getId(), clothes.getOwnerId(), clothes.getName(),
                clothes.getImageUrl(), clothes.getType(), List.of());
    }

    @Transactional
    public void delete(UUID uuid){
        if(clothesRepository.existsById(uuid)) {
            clothesRepository.deleteById(uuid);
        }else{
            throw new OtbooException(ErrorCode.NOT_FOUND_CLOTHES);
        }
    }


}
