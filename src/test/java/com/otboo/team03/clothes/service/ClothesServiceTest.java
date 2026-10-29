package com.otboo.team03.clothes.service;

import com.otboo.team03.clothes.dto.request.ClothesCreateRequest;
import com.otboo.team03.clothes.dto.request.ClothesAttributeDto;
import com.otboo.team03.clothes.dto.response.ClothesAttributeWithDefDto;
import com.otboo.team03.clothes.entity.ClothesAttribute;
import com.otboo.team03.clothes.entity.ClothesAttributeDefinition;
import com.otboo.team03.clothes.repository.ClothesAttributeRepository;
import com.otboo.team03.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.team03.clothes.dto.response.ClothesDto;
import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.user.exception.UserNotFoundException;
import com.otboo.team03.user.repository.UserRepository;
import com.otboo.team03.user.entity.User;
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

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClothesAttributeRepository clothesAttributeRepository;
    @Mock
    private ClothesAttributeDefinitionRepository clothesAttributeDefinitionRepository;

    // 실제 검증 대상이다. 위 가짜 Repository를 생성자에 주입한다.
    @InjectMocks
    private ClothesService clothesService;

    @Test
    @DisplayName("생성 요청의 기본 정보를 저장하고 저장 결과를 DTO로 반환한다")
    void create() {
        // given: 요청과 DB 저장 후 반환될 객체를 준비한다.
        UUID ownerId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        ClothesCreateRequest request = new ClothesCreateRequest(ownerId, "반팔", ClothesType.TOP,
                List.of(new ClothesAttributeDto(definitionId, "파랑")));
        Clothes saved = new Clothes("반팔", ClothesType.TOP, null, owner(ownerId));
        // 실제 JPA를 실행하지 않으므로, DB가 발급할 ID를 테스트에서 대신 설정한다.
        ReflectionTestUtils.setField(saved, "id", clothesId);
        // 소유자가 존재해야 생성 검증을 통과한다.
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner(ownerId)));
        when(clothesRepository.save(any(Clothes.class))).thenReturn(saved);
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("색상", List.of("파랑", "빨강"));
        ReflectionTestUtils.setField(definition, "id", definitionId);
        when(clothesAttributeDefinitionRepository.findById(definitionId)).thenReturn(Optional.of(definition));

        // when: 실제 Service의 생성 메서드를 실행한다.
        ClothesDto result = clothesService.create(request);

        // then: 저장에 전달한 객체를 붙잡아 요청 값이 제대로 옮겨졌는지 확인한다.
        ArgumentCaptor<Clothes> captor = ArgumentCaptor.forClass(Clothes.class);
        verify(clothesRepository).save(captor.capture());
        Clothes submitted = captor.getValue();
        assertThat(submitted.getOwner().getId()).isEqualTo(ownerId);
        assertThat(submitted.getName()).isEqualTo("반팔");
        assertThat(submitted.getType()).isEqualTo(ClothesType.TOP);
        assertThat(submitted.getImageUrl()).isNull();
        // 생성 ID를 포함한 저장 결과가 응답 DTO에 반영됐는지도 확인한다.
        assertThat(result).isEqualTo(new ClothesDto(clothesId, ownerId, "반팔", null, ClothesType.TOP,
                List.of(new ClothesAttributeWithDefDto(definitionId, "색상", List.of("파랑", "빨강"), "파랑"))));
        ArgumentCaptor<ClothesAttribute> attributeCaptor = ArgumentCaptor.forClass(ClothesAttribute.class);
        verify(clothesAttributeRepository).save(attributeCaptor.capture());
        assertThat(attributeCaptor.getValue().getClothes()).isSameAs(saved);
        assertThat(attributeCaptor.getValue().getDefinition()).isSameAs(definition);
        assertThat(attributeCaptor.getValue().getValue()).isEqualTo("파랑");
    }

    @Test
    @DisplayName("소유자가 없으면 사용자 없음 예외가 발생하고 의상을 저장하지 않는다")
    void createWhenOwnerMissing() {
        // given: 요청에 ID는 있지만 실제 사용자는 없는 상황이다.
        UUID ownerId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        ClothesCreateRequest request = new ClothesCreateRequest(ownerId, "반팔", ClothesType.TOP,
                List.of(new ClothesAttributeDto(definitionId, "파랑")));
        when(userRepository.findById(ownerId)).thenReturn(Optional.empty());

        // when + then: 예외 종류와 에러 코드를 확인한다.
        assertThatThrownBy(() -> clothesService.create(request))
                .isInstanceOfSatisfying(UserNotFoundException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_USER));
        // 검증 실패 후에는 의상 Repository를 호출하지 않아야 한다.
        verifyNoInteractions(clothesRepository);
    }

    @Test
    @DisplayName("존재하는 의상을 조회하면 저장된 정보를 DTO로 반환한다")
    void findById() {
        // given: Optional.of는 조회 결과가 있는 상황을 표현한다.
        UUID clothesId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Clothes clothes = new Clothes("청바지", ClothesType.BOTTOM, "https://example.com/jeans.png", owner(ownerId));
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
        Clothes clothes = new Clothes("기존 이름", ClothesType.TOP, "image.png", owner(ownerId));
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
        Clothes clothes = new Clothes("기존 이름", ClothesType.TOP, null, owner(UUID.randomUUID()));
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
    @Test
    @DisplayName("없는 속성 정의는 의상 저장 전에 거절한다")
    void createWithMissingDefinition() {
        UUID owner = UUID.randomUUID();
        UUID definition = UUID.randomUUID();
        when(userRepository.findById(owner)).thenReturn(Optional.of(owner(owner)));
        when(clothesAttributeDefinitionRepository.findById(definition)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> clothesService.create(new ClothesCreateRequest(owner, "반팔", ClothesType.TOP,
                List.of(new ClothesAttributeDto(definition, "파랑")))))
                .isInstanceOfSatisfying(OtbooException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_ATTRIBUTE_DEFINITION));
        verifyNoInteractions(clothesRepository, clothesAttributeRepository);
    }

    @Test
    @DisplayName("허용되지 않은 선택값은 의상 저장 전에 거절한다")
    void createWithInvalidValue() {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(userRepository.findById(owner)).thenReturn(Optional.of(owner(owner)));
        when(clothesAttributeDefinitionRepository.findById(id)).thenReturn(Optional.of(
                new ClothesAttributeDefinition("색상", List.of("파랑"))));
        assertThatThrownBy(() -> clothesService.create(new ClothesCreateRequest(owner, "반팔", ClothesType.TOP,
                List.of(new ClothesAttributeDto(id, "철")))))
                .isInstanceOfSatisfying(OtbooException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_CLOTHES_ATTRIBUTE_VALUE));
        verifyNoInteractions(clothesRepository, clothesAttributeRepository);
    }

    @Test
    @DisplayName("중복 정의는 재조회하거나 저장하지 않고 거절한다")
    void createWithDuplicateDefinition() {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(userRepository.findById(owner)).thenReturn(Optional.of(owner(owner)));
        when(clothesAttributeDefinitionRepository.findById(id)).thenReturn(Optional.of(
                new ClothesAttributeDefinition("색상", List.of("파랑"))));
        ClothesAttributeDto attribute = new ClothesAttributeDto(id, "파랑");
        assertThatThrownBy(() -> clothesService.create(new ClothesCreateRequest(owner, "반팔", ClothesType.TOP,
                List.of(attribute, attribute))))
                .isInstanceOfSatisfying(OtbooException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_ATTRIBUTE));
        verify(clothesAttributeDefinitionRepository, times(1)).findById(id);
        verifyNoInteractions(clothesRepository, clothesAttributeRepository);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @org.junit.jupiter.params.provider.EmptySource
    @DisplayName("Service를 직접 호출해도 속성 목록 누락과 빈 목록을 거절한다")
    void createWithoutAttributes(List<ClothesAttributeDto> attributes) {
        assertThatThrownBy(() -> clothesService.create(new ClothesCreateRequest(
                UUID.randomUUID(), "반팔", ClothesType.TOP, attributes)))
                .isInstanceOfSatisfying(OtbooException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(userRepository, clothesRepository, clothesAttributeRepository,
                clothesAttributeDefinitionRepository);
    }

    @Test
    @DisplayName("Service를 직접 호출해도 null 속성 항목을 저장하지 않는다")
    void createWithNullAttribute() {
        UUID ownerId = UUID.randomUUID();
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner(ownerId)));
        assertThatThrownBy(() -> clothesService.create(new ClothesCreateRequest(ownerId, "반팔", ClothesType.TOP,
                java.util.Collections.singletonList(null))))
                .isInstanceOfSatisfying(OtbooException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(clothesRepository, clothesAttributeRepository, clothesAttributeDefinitionRepository);
    }
    @Test
    @DisplayName("단건 조회 응답에 속성 정의와 실제 선택값을 포함한다")
    void findByIdWithAttributes() {
        UUID id = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        Clothes clothes = new Clothes("반팔", ClothesType.TOP, null, owner(UUID.randomUUID()));
        ReflectionTestUtils.setField(clothes, "id", id);
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("색상", List.of("파랑", "빨강"));
        ReflectionTestUtils.setField(definition, "id", definitionId);
        when(clothesRepository.findById(id)).thenReturn(Optional.of(clothes));
        when(clothesAttributeRepository.findAllByClothes_Id(id)).thenReturn(
                List.of(new ClothesAttribute(clothes, definition, "파랑")));

        ClothesDto result = clothesService.findById(id);

        assertThat(result.attributes()).containsExactly(new ClothesAttributeWithDefDto(
                definitionId, "색상", List.of("파랑", "빨강"), "파랑"));
        verify(clothesAttributeRepository).findAllByClothes_Id(id);
    }
    @Test
    @DisplayName("목록의 속성을 한 번만 조회하고 각 의상에 맞게 연결한다")
    void findAllByOwnerIdWithAttributes() {
        UUID owner = UUID.randomUUID();
        Clothes top = new Clothes("반팔", ClothesType.TOP, null, owner(owner));
        Clothes pants = new Clothes("바지", ClothesType.BOTTOM, null, owner(owner));
        Clothes hat = new Clothes("모자", ClothesType.HAT, null, owner(owner));
        for (Clothes clothes : List.of(top, pants, hat)) {
            ReflectionTestUtils.setField(clothes, "id", UUID.randomUUID());
        }
        ClothesAttributeDefinition color = new ClothesAttributeDefinition("색상", List.of("파랑", "빨강"));
        ReflectionTestUtils.setField(color, "id", UUID.randomUUID());
        List<UUID> ids = List.of(top.getId(), pants.getId(), hat.getId());
        when(clothesRepository.findAllByOwner_Id(owner)).thenReturn(List.of(top, pants, hat));
        // DB 반환 순서가 의상 목록 순서와 달라도 올바르게 묶여야 한다.
        when(clothesAttributeRepository.findAllByClothes_IdIn(ids)).thenReturn(List.of(
                new ClothesAttribute(pants, color, "빨강"), new ClothesAttribute(top, color, "파랑")));

        List<ClothesDto> result = clothesService.findAllByOwnerId(owner);

        assertThat(result).extracting(ClothesDto::id).containsExactlyElementsOf(ids);
        assertThat(result.get(0).attributes()).extracting(ClothesAttributeWithDefDto::value).containsExactly("파랑");
        assertThat(result.get(1).attributes()).extracting(ClothesAttributeWithDefDto::value).containsExactly("빨강");
        assertThat(result.get(2).attributes()).isEmpty();
        verify(clothesRepository).findAllByOwner_Id(owner);
        verify(clothesAttributeRepository).findAllByClothes_IdIn(ids);
        verifyNoMoreInteractions(clothesAttributeRepository);
    }

    @Test
    @DisplayName("의상이 없으면 속성을 조회하지 않고 빈 목록을 반환한다")
    void findAllByOwnerIdWhenEmpty() {
        UUID owner = UUID.randomUUID();
        when(clothesRepository.findAllByOwner_Id(owner)).thenReturn(List.of());

        assertThat(clothesService.findAllByOwnerId(owner)).isEmpty();
        verifyNoInteractions(clothesAttributeRepository);
    }
    // DB 없이도 소유자 ID를 가진 User 객체를 준비한다.
    private User owner(UUID id) {
        User user = new User(id + "@example.com", "encoded", "소유자");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
