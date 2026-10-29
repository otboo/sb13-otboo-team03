package com.otboo.team03.clothes.service;

import com.otboo.team03.clothes.dto.request.ClothesAttributeDto;
import com.otboo.team03.clothes.dto.request.ClothesCreateRequest;
import com.otboo.team03.clothes.dto.request.ClothesUpdateRequest;
import com.otboo.team03.clothes.dto.response.ClothesAttributeWithDefDto;
import com.otboo.team03.clothes.dto.response.ClothesDto;
import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesAttribute;
import com.otboo.team03.clothes.entity.ClothesAttributeDefinition;
import com.otboo.team03.clothes.exception.ClothesAttributeDefinitionNotFoundException;
import com.otboo.team03.clothes.exception.ClothesNotFoundException;
import com.otboo.team03.clothes.exception.DuplicateAttributeException;
import com.otboo.team03.clothes.exception.InvalidClothesAttributeValueException;
import com.otboo.team03.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.team03.clothes.repository.ClothesAttributeRepository;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.user.exception.UserNotFoundException;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ClothesService {
    private final ClothesRepository clothesRepository;
    //속성 정의 조회
    private final ClothesAttributeDefinitionRepository clothesAttributeDefinitionRepository;
    //의상에 선택한 속성 저장
    private final ClothesAttributeRepository clothesAttributeRepository;

    private final UserRepository userRepository;


    /**
     * 사용자와 모든 속성을 검증한 뒤 의상과 속성을 함께 저장한다.
     * 저장 중 RuntimeException이 발생하면 이 트랜잭션의 변경을 모두 롤백한다.
     */
    @Transactional
    public ClothesDto create(ClothesCreateRequest request){
        // Controller의 @Valid를 거치지 않는 내부 호출에서도 필수 값과 최소 한 개 규칙을 보장한다.
        if (request == null || request.ownerId() == null || request.type() == null
                || request.name() == null || request.name().isBlank() || request.name().length() > 255
                || request.attributes() == null || request.attributes().isEmpty()) {
            throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
        }
        // 존재 여부 확인은 권한 검사가 아니다.
        //인증 사용자 ID를 제공하는 구조가 구현되면 ownerId와 비교하거나 소유자로 사용한다.
        // 존재 여부만 확인하는 대신 실제 User를 조회해 의상의 소유자로 연결한다.
        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(UserNotFoundException::new);
        Set<UUID> definitionIds = new HashSet<>();
        // 검증한 정의를 ID별로 보관해 저장 단계에서 같은 정의를 다시 조회하지 않는다.
        Map<UUID, ClothesAttributeDefinition> definitions = new HashMap<>();

        for(ClothesAttributeDto attribute : request.attributes()){
            // [null] 또는 필수 값이 빠진 항목을 내부 호출로 전달해도 저장하지 않는다.
            if (attribute == null || attribute.definitionId() == null
                    || attribute.value() == null || attribute.value().isBlank()
                    || attribute.value().length() > 255) {
                throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
            }
            // 이미 확인한 정의 ID이면 DB를 다시 조회하지 않고 중복 예외를 발생시킨다.
            if (!definitionIds.add(attribute.definitionId())) {
                throw new DuplicateAttributeException();
            }

            //속성 정의 조회와 선택값 검증
            Optional<ClothesAttributeDefinition> definition =
                    clothesAttributeDefinitionRepository.findById(attribute.definitionId());

            if(definition.isEmpty()){
                throw new ClothesAttributeDefinitionNotFoundException();
            }
            ClothesAttributeDefinition attributeDefinition = definition.get();
            if (!attributeDefinition.getSelectableValues().contains(attribute.value())) {
                // 정의 존재 여부와 별개로, 선택한 값이 그 정의의 허용 목록에 있어야 한다.
                throw new InvalidClothesAttributeValueException();
            }
            definitions.put(attribute.definitionId(), attributeDefinition);
        }
        // 모든 속성 검증이 끝나기 전에는 의상이나 속성을 저장하지 않는다.
        //이미지 업로드/저장소 연동은 미구현이다. 현재 이미지 URL은 null이다.
        Clothes clothes = new Clothes(request.name(),request.type(),null,owner);
        Clothes savedClothes = clothesRepository.save(clothes);

        //응답으로 반환할 속성 DTO 목록
        List<ClothesAttributeWithDefDto> attributeWithDefDtos = new ArrayList<>();

        for (ClothesAttributeDto attribute : request.attributes()) {
            ClothesAttributeDefinition attributeDefinition =
                    definitions.get(attribute.definitionId());

            // 저장된 의상과 검증한 정의를 연결한다. 선택한 값은 별도 속성 행으로 저장된다.
            ClothesAttribute clothesAttribute = new ClothesAttribute(
                    savedClothes,
                    attributeDefinition,
                    attribute.value()
            );
            clothesAttributeRepository.save(clothesAttribute);

            ClothesAttributeWithDefDto attributeDto = new ClothesAttributeWithDefDto(
                    attributeDefinition.getId(),
                    attributeDefinition.getName(),
                    // 응답 목록을 변경해도 관리 중인 엔티티의 선택값 목록이 바뀌지 않도록 복사한다.
                    List.copyOf(attributeDefinition.getSelectableValues()),
                    attribute.value()
            );

            attributeWithDefDtos.add(attributeDto);
        }
        return ClothesDto.from(savedClothes, List.copyOf(attributeWithDefDtos));
    }

    @Transactional(readOnly = true)
    public ClothesDto findById(UUID clothesId) {
        Optional<Clothes> clothesOptional = clothesRepository.findById(clothesId);
        // 조회 결과가 없으면 DTO를 만들지 않고 예외 처리로 넘어간다.
        if (clothesOptional.isEmpty()) {
            throw new ClothesNotFoundException();
        }
        // 의상 기본 정보를 꺼내고, 해당 ID에 연결된 속성을 조회한다.
        Clothes clothes = clothesOptional.get();
        List<ClothesAttribute> attributes =
                clothesAttributeRepository.findAllByClothes_Id(clothesId);
        // 변환한 목록을 실제 응답에 전달해야 저장된 속성이 JSON에 포함된다.
        List<ClothesAttributeWithDefDto> attributeDtos = toAttributeDtos(attributes);
        return ClothesDto.from(clothes, attributeDtos);
    }

    /**
     * 조회한 속성 엔티티를 응답 DTO 목록으로 변환한다. DB에 새 데이터를 생성하지 않는다.
     * 속성이 없으면 빈 목록을 반환한다.
     */
    private static List<ClothesAttributeWithDefDto> toAttributeDtos(List<ClothesAttribute> attributes) {
        List<ClothesAttributeWithDefDto> attributeDtos = new ArrayList<>();
        for (ClothesAttribute attribute : attributes) {
            ClothesAttributeDefinition definition = attribute.getDefinition();

            // 정의에는 이름과 선택지가, 속성에는 사용자가 선택한 실제 값이 들어 있다.
            ClothesAttributeWithDefDto attributeDto = new ClothesAttributeWithDefDto(
                    definition.getId(),
                    definition.getName(),
                    List.copyOf(definition.getSelectableValues()),
                    attribute.getValue()
            );

            attributeDtos.add(attributeDto);
        }
        return List.copyOf(attributeDtos);
    }

    @Transactional(readOnly = true)
    public List<ClothesDto> findAllByOwnerId(UUID ownerId) {
        List<Clothes> clothesList = clothesRepository.findAllByOwner_Id(ownerId);
        // 의상이 없으면 불필요한 속성 조회와 빈 IN 조건을 피한다.
        if (clothesList.isEmpty()) {
            return List.of();
        }
        List<UUID> clothesIds = clothesList.stream().map(Clothes::getId).toList();
        // 반복문 안에서 DB를 조회하지 않고, 모든 속성을 가져와 의상 ID별로 묶는다.
        Map<UUID, List<ClothesAttribute>> attributesByClothesId = new HashMap<>();
        for (ClothesAttribute attribute : clothesAttributeRepository.findAllByClothes_IdIn(clothesIds)) {
            attributesByClothesId.computeIfAbsent(attribute.getClothes().getId(), id -> new ArrayList<>())
                    .add(attribute);
        }
        List<ClothesDto> clothesResponses = new ArrayList<>();
        for (Clothes clothes : clothesList) {
            // 속성이 없는 의상도 제외하지 않고 빈 속성 목록으로 응답한다.
            List<ClothesAttribute> attributes = attributesByClothesId.getOrDefault(clothes.getId(), List.of());
            List<ClothesAttributeWithDefDto> attributeDtos = toAttributeDtos(attributes);
            clothesResponses.add(ClothesDto.from(clothes, attributeDtos));
        }
        // 각 의상의 기본 정보와 속성을 담은 목록을 반환한다. 의상이 없으면 빈 목록이다.
        return clothesResponses;
    }
    @Transactional
    public ClothesDto update(UUID clothesId, ClothesUpdateRequest request) {
        Optional<Clothes> clothesOptional = clothesRepository.findById(clothesId);
        if (clothesOptional.isEmpty()) {
            throw new ClothesNotFoundException();
        }

        Clothes clothes = clothesOptional.get();
        // 트랜잭션 안에서 조회한 엔티티는 변경 감지로 수정 내용이 DB에 반영된다.
        clothes.update(request.name(), request.type());

        // 속성 수정은 아직 구현하지 않았으므로 기본 정보만 반환한다.
        return new ClothesDto(clothes.getId(), clothes.getOwner().getId(), clothes.getName(),
                clothes.getImageUrl(), clothes.getType(), List.of());
    }

    @Transactional
    public void delete(UUID uuid){
        if(clothesRepository.existsById(uuid)) {
            clothesRepository.deleteById(uuid);
        }else{
            throw new ClothesNotFoundException();
        }
    }




}
