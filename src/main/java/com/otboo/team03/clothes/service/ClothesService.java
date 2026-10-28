package com.otboo.team03.clothes.service;

import com.otboo.team03.clothes.dto.request.ClothesCreateRequest;
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

}
