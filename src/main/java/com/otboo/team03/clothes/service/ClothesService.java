package com.otboo.team03.clothes.service;

import com.otboo.team03.clothes.dto.request.ClothesAttributeDto;
import com.otboo.team03.clothes.dto.request.ClothesCreateRequest;
import com.otboo.team03.clothes.dto.request.ClothesUpdateRequest;
import com.otboo.team03.clothes.dto.response.ClothesAttributeWithDefDto;
import com.otboo.team03.clothes.dto.response.ClothesDto;
import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesAttribute;
import com.otboo.team03.clothes.entity.ClothesAttributeDefinition;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.clothes.exception.ClothesAttributeDefinitionNotFoundException;
import com.otboo.team03.clothes.exception.ClothesNotFoundException;
import com.otboo.team03.clothes.exception.DuplicateAttributeException;
import com.otboo.team03.clothes.exception.InvalidClothesAttributeValueException;
import com.otboo.team03.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.team03.clothes.repository.ClothesAttributeRepository;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.global.dto.CursorResponse;
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
        return toClothesDtos(clothesRepository.findAllByOwner_Id(ownerId));
    }
    /**
     * 본인 의상을 수정한다. attributes 생략/null은 유지, 빈 목록은 전체 제거,
     * 목록 전달은 전체 교체로 처리한다. 의상이나 공유 속성 정의 자체는 삭제하지 않는다.
     */
    @Transactional
    public ClothesDto update(UUID clothesId, ClothesUpdateRequest request, UUID requesterId) {
        Clothes clothes = clothesRepository.findById(clothesId)
                .orElseThrow(ClothesNotFoundException::new);
        if (!clothes.getOwner().getId().equals(requesterId)) {
            throw new OtbooException(ErrorCode.ACCESS_DENIED);
        }
        if (request == null || (request.name() != null
                && (request.name().isBlank() || request.name().length() > 255))) {
            throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 모든 입력 검증을 끝낸 뒤에만 엔티티를 변경한다.
        Map<UUID, ClothesAttributeDefinition> definitions = request.attributes() == null
                ? null : validateAttributes(request.attributes());
        List<ClothesAttribute> existing = clothesAttributeRepository.findAllByClothes_Id(clothesId);
        List<ClothesAttribute> resultAttributes = new ArrayList<>(existing);
        if (definitions != null) {
            Map<UUID, ClothesAttribute> remaining = new HashMap<>();
            for (ClothesAttribute attribute : existing) {
                remaining.put(attribute.getDefinition().getId(), attribute);
            }
            resultAttributes.clear();
            for (ClothesAttributeDto attribute : request.attributes()) {
                UUID definitionId = attribute.definitionId();
                // 기존 복합 키는 유지하고 선택값만 변경한다. 같은 키를 삭제 후 재등록하지 않는다.
                ClothesAttribute current = remaining.remove(definitionId);
                if (current != null) {
                    current.updateValue(attribute.value());
                    resultAttributes.add(current);
                } else {
                    resultAttributes.add(clothesAttributeRepository.save(new ClothesAttribute(
                            clothes, definitions.get(definitionId), attribute.value())));
                }
            }
            // 새 목록에 빠진 의상별 속성값만 제거한다. 피드 연결은 건드리지 않는다.
            clothesAttributeRepository.deleteAll(remaining.values());
        }
        clothes.update(request.name(), request.type());
        return ClothesDto.from(clothes, toAttributeDtos(resultAttributes));
    }

    @Transactional
    public void delete(UUID uuid){
        if(clothesRepository.existsById(uuid)) {
            clothesRepository.deleteById(uuid);
        }else{
            throw new ClothesNotFoundException();
        }
    }
    /**
     * 소유자와 타입 조건에 맞는 의상을 커서 방식으로 조회한다.
     *
     * 정렬: name ASC, id ASC.
     * cursor에는 마지막 의상 이름, idAfter에는 마지막 의상 ID를 전달한다.
     * totalCount는 커서 이후 개수가 아니라 소유자·타입 조건에 맞는 전체 개수다.
     */
    @Transactional(readOnly = true)
    public CursorResponse<ClothesDto> findAll(
            UUID ownerId,
            int limit,
            ClothesType typeEqual,
            String cursor,
            UUID idAfter
    ) {
        // 서비스 직접 호출에서도 기본 요청 조건을 보장한다.
        if (ownerId == null || limit <= 0 || limit == Integer.MAX_VALUE) {
            throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 이 구현은 이름과 ID를 한 쌍으로 사용한다.
        // 첫 페이지는 둘 다 없고, 다음 페이지는 둘 다 있어야 한다.
        if ((cursor == null) != (idAfter == null)) {
            throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 한 개 더 조회하면 다음 페이지가 있는지 알 수 있다.
        List<Clothes> fetched = clothesRepository.findPage(
                ownerId,
                typeEqual,
                cursor,
                idAfter,
                limit + 1
        );

        boolean hasNext = fetched.size() > limit;

        // 확인용으로 더 가져온 한 개는 응답에 포함하지 않는다.
        List<Clothes> page = hasNext
                ? fetched.subList(0, limit)
                : fetched;

        // 커서 조건을 제외한 전체 개수를 센다.
        long totalCount = clothesRepository.countByFilter(
                ownerId,
                typeEqual
        );

        List<ClothesDto> data = toClothesDtos(page);

        String nextCursor = null;
        UUID nextIdAfter = null;

        if (hasNext) {
            // 더 가져온 의상이 아니라 실제 반환한 마지막 의상을 기준으로 한다.
            Clothes last = page.get(page.size() - 1);
            nextCursor = last.getName();
            nextIdAfter = last.getId();
        }

        return new CursorResponse<>(
                data,
                nextCursor,
                nextIdAfter,
                hasNext,
                totalCount,
                "name",
                "ASCENDING"
        );
    }

    /** 현재 페이지의 속성·정의를 일괄 조회하고 의상의 정렬 순서를 유지해 응답을 만든다. */
    private List<ClothesDto> toClothesDtos(List<Clothes> clothesList) {
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

    /**
     * 교체할 속성 목록을 검증하고, 저장에 사용할 정의를 반환한다.
     * 이 메서드에서는 기존 속성을 삭제하거나 변경하지 않는다.
     */
    private Map<UUID, ClothesAttributeDefinition> validateAttributes(
            List<ClothesAttributeDto> attributes
    ) {
        Map<UUID, ClothesAttributeDefinition> definitions = new HashMap<>();

        for (ClothesAttributeDto attribute : attributes) {
            if (attribute == null
                    || attribute.definitionId() == null
                    || attribute.value() == null
                    || attribute.value().isBlank()
                    || attribute.value().length() > 255) {
                throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
            }

            UUID definitionId = attribute.definitionId();

            // 한 의상에 같은 정의를 두 번 지정할 수 없다.
            if (definitions.containsKey(definitionId)) {
                throw new DuplicateAttributeException();
            }

            ClothesAttributeDefinition definition =
                    clothesAttributeDefinitionRepository.findById(definitionId)
                            .orElseThrow(
                                    ClothesAttributeDefinitionNotFoundException::new
                            );

            // 정의가 존재하더라도 허용된 선택값인지 별도로 확인한다.
            if (!definition.getSelectableValues().contains(attribute.value())) {
                throw new InvalidClothesAttributeValueException();
            }

            definitions.put(definitionId, definition);
        }

        return definitions;
    }
}
