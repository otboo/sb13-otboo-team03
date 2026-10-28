package com.otboo.team03.clothes.service;

import com.otboo.team03.clothes.dto.request.ClothesCreateRequest;
import com.otboo.team03.clothes.dto.response.ClothesDto;
import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Mockito가 @Mock과 @InjectMocks를 처리하도록 연결한다.
@ExtendWith(MockitoExtension.class)
class ClothesServiceTest {
    // 실제 DB에 접근하지 않는 가짜 Repository다. 반환값은 각 테스트에서 지정한다.
    @Mock
    private ClothesRepository clothesRepository;

    // 실제 검증 대상이다. 위 가짜 Repository를 생성자에 주입한다.
    @InjectMocks
    private ClothesService clothesService;

    @Test
    @DisplayName("생성 요청의 기본 정보를 저장하고 저장 결과를 DTO로 반환한다")
    void create() {
        // given: 요청과 DB 저장 후 반환될 객체를 준비한다.
        UUID ownerId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        ClothesCreateRequest request = new ClothesCreateRequest(ownerId, "반팔", ClothesType.TOP, List.of());
        Clothes saved = new Clothes("반팔", ClothesType.TOP, null, ownerId);
        // 실제 JPA를 실행하지 않으므로, DB가 발급할 ID를 테스트에서 대신 설정한다.
        ReflectionTestUtils.setField(saved, "id", clothesId);
        when(clothesRepository.save(any(Clothes.class))).thenReturn(saved);

        // when: 실제 Service의 생성 메서드를 실행한다.
        ClothesDto result = clothesService.create(request);

        // then: 저장에 전달한 객체를 붙잡아 요청 값이 제대로 옮겨졌는지 확인한다.
        ArgumentCaptor<Clothes> captor = ArgumentCaptor.forClass(Clothes.class);
        verify(clothesRepository).save(captor.capture());
        Clothes submitted = captor.getValue();
        assertThat(submitted.getOwnerId()).isEqualTo(ownerId);
        assertThat(submitted.getName()).isEqualTo("반팔");
        assertThat(submitted.getType()).isEqualTo(ClothesType.TOP);
        assertThat(submitted.getImageUrl()).isNull();
        // 생성 ID를 포함한 저장 결과가 응답 DTO에 반영됐는지도 확인한다.
        assertThat(result).isEqualTo(new ClothesDto(clothesId, ownerId, "반팔", null, ClothesType.TOP, List.of()));
    }

    @Test
    @DisplayName("존재하는 의상을 조회하면 저장된 정보를 DTO로 반환한다")
    void findById() {
        // given: Optional.of는 조회 결과가 있는 상황을 표현한다.
        UUID clothesId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Clothes clothes = new Clothes("청바지", ClothesType.BOTTOM, "https://example.com/jeans.png", ownerId);
        ReflectionTestUtils.setField(clothes, "id", clothesId);
        when(clothesRepository.findById(clothesId)).thenReturn(Optional.of(clothes));

        // when
        ClothesDto result = clothesService.findById(clothesId);

        // then: 이름뿐 아니라 ID, 소유자, 이미지, 타입까지 확인한다.
        assertThat(result).isEqualTo(new ClothesDto(clothesId, ownerId, "청바지", clothes.getImageUrl(), ClothesType.BOTTOM, List.of()));
        // 조회가 데이터를 저장하는 작업을 호출하지 않는지도 확인한다.
        verify(clothesRepository, never()).save(any());
    }

    @Test
    @DisplayName("없는 의상을 조회하면 NOT_FOUND_CLOTHES 예외가 발생한다")
    void findByIdWhenMissing() {
        // given: Optional.empty는 조회된 의상이 없는 상황이다.
        UUID clothesId = UUID.randomUUID();
        when(clothesRepository.findById(clothesId)).thenReturn(Optional.empty());

        // when + then: 예외 종류와 그 안의 에러 코드를 함께 검증한다.
        assertThatThrownBy(() -> clothesService.findById(clothesId))
                .isInstanceOfSatisfying(OtbooException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_CLOTHES));
    }
    @Test
    @DisplayName("수정 시 전달한 이름만 변경하고 생략한 타입과 소유자는 유지한다")
    void updateNameOnly() {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Clothes clothes = new Clothes("기존 이름", ClothesType.TOP, "image.png", ownerId);
        ReflectionTestUtils.setField(clothes, "id", id);
        when(clothesRepository.findById(id)).thenReturn(Optional.of(clothes));

        ClothesDto result = clothesService.update(id,
                new com.otboo.team03.clothes.dto.request.ClothesUpdateRequest("새 이름", null, null));

        assertThat(clothes.getName()).isEqualTo("새 이름");
        assertThat(result).isEqualTo(new ClothesDto(id, ownerId, "새 이름", "image.png", ClothesType.TOP, List.of()));
    }

    @Test
    @DisplayName("수정 시 이름이 생략되면 유지하고 타입을 변경한다")
    void updateTypeOnly() {
        UUID id = UUID.randomUUID();
        Clothes clothes = new Clothes("기존 이름", ClothesType.TOP, null, UUID.randomUUID());
        when(clothesRepository.findById(id)).thenReturn(Optional.of(clothes));

        ClothesDto result = clothesService.update(id,
                new com.otboo.team03.clothes.dto.request.ClothesUpdateRequest(null, ClothesType.OUTER, null));

        assertThat(result.name()).isEqualTo("기존 이름");
        assertThat(result.type()).isEqualTo(ClothesType.OUTER);
    }

    @Test
    @DisplayName("없는 의상을 수정하면 예외를 발생시킨다")
    void updateWhenMissing() {
        UUID id = UUID.randomUUID();
        when(clothesRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> clothesService.update(id,
                new com.otboo.team03.clothes.dto.request.ClothesUpdateRequest("새 이름", null, null)))
                .isInstanceOfSatisfying(OtbooException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_CLOTHES));
    }
}
