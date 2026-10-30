package com.otboo.team03.clothes.controller;

import com.otboo.team03.clothes.dto.request.ClothesCreateRequest;
import com.otboo.team03.clothes.dto.response.ClothesDto;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.clothes.service.ClothesService;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.GlobalExceptionHandler;
import com.otboo.team03.global.exception.OtbooException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ClothesControllerTest {
    private ClothesService service;
    private MockMvc mvc;
    private final UUID ownerId = UUID.randomUUID();
    private final UUID definitionId = UUID.randomUUID();
    private final UUID clothesId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Controller만 검증하므로 Service는 가짜로 대체한다.
        service = mock(ClothesService.class);
        // 서버를 띄우지 않고 HTTP 요청, JSON 변환, 검증, 예외 응답을 확인한다.
        // 전체 Spring 설정이나 DB 연결을 검증하는 통합 테스트는 아니다.
        mvc = MockMvcBuilders.standaloneSetup(new ClothesController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    @DisplayName("생성 요청에 201과 생성된 의상 JSON을 반환한다")
    void create() throws Exception {
        // Controller의 소유자 검사를 통과하도록 서버가 인증한 사용자 정보를 전달한다.
        var details = org.mockito.Mockito.mock(
                com.otboo.team03.global.security.OtbooUserDetails.class,
                org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(details.getUserDto().id()).thenReturn(ownerId);
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                details, null, java.util.List.of());
        // given: Service가 성공했을 때 반환할 결과를 지정한다.
        when(service.create(any())).thenReturn(response());

        // when + then: 프론트처럼 JSON 요청을 보내 상태 코드와 응답 필드를 확인한다.
        mvc.perform(post("/api/clothes").principal(authentication).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerId":"%s","name":"반팔","type":"TOP","attributes":[{"definitionId":"%s","value":"파랑"}]}
                                """.formatted(ownerId, definitionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(clothesId.toString()))
                .andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
                .andExpect(jsonPath("$.name").value("반팔"))
                .andExpect(jsonPath("$.type").value("TOP"));
        // JSON이 요청 DTO로 변환되어 Service에 전달됐는지도 검증한다.
        verify(service).create(new ClothesCreateRequest(ownerId, "반팔", ClothesType.TOP, List.of(new com.otboo.team03.clothes.dto.request.ClothesAttributeDto(definitionId, "파랑"))));
    }

    @Test
    @DisplayName("필수 값이 빠진 생성 요청은 400으로 거절한다")
    void createWhenInvalid() throws Exception {
        mvc.perform(post("/api/clothes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest());
        // @Valid에서 거절되면 Service가 호출되면 안 된다.
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("단건 조회 요청에 200과 의상 JSON을 반환한다")
    void findById() throws Exception {
        when(service.findById(clothesId)).thenReturn(response());

        mvc.perform(get("/api/clothes/{clothesId}", clothesId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clothesId.toString()))
                .andExpect(jsonPath("$.name").value("반팔"));
        // URL의 ID가 UUID로 변환되어 Service로 전달됐는지 확인한다.
        verify(service).findById(clothesId);
    }

    @Test
    @DisplayName("조회 대상이 없다는 예외를 HTTP 404로 변환한다")
    void findByIdWhenMissing() throws Exception {
        when(service.findById(clothesId)).thenThrow(new OtbooException(ErrorCode.NOT_FOUND_CLOTHES));

        // GlobalExceptionHandler가 Service의 예외를 404 응답으로 바꾸는지 확인한다.
        mvc.perform(get("/api/clothes/{clothesId}", clothesId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ownerId 쿼리 파라미터를 전달하고 의상 목록을 반환한다")
    void findAllByOwnerId() throws Exception {
        when(service.findAll(ownerId, 20, null, null, null)).thenReturn(new com.otboo.team03.global.dto.CursorResponse<>(List.of(response()), null, null, false, 1, "name", "ASCENDING"));

        mvc.perform(get("/api/clothes").param("ownerId", ownerId.toString()).param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(clothesId.toString()))
                .andExpect(jsonPath("$.data[0].ownerId").value(ownerId.toString()));
        verify(service).findAll(ownerId, 20, null, null, null);
    }

    @Test
    @DisplayName("조회된 의상이 없으면 200과 빈 배열을 반환한다")
    void findAllByOwnerIdWhenEmpty() throws Exception {
        when(service.findAll(ownerId, 20, null, null, null)).thenReturn(new com.otboo.team03.global.dto.CursorResponse<>(List.of(), null, null, false, 0, "name", "ASCENDING"));

        mvc.perform(get("/api/clothes").param("ownerId", ownerId.toString()).param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        verify(service).findAll(ownerId, 20, null, null, null);
    }

    // 필터 없이 Controller를 호출하여 각 인증 거부 분기가 서비스를 호출하지 않는지 검증한다.
    @Test
    void rejectsInvalidAuthenticationForCreateAndUpdate() throws Exception {
        var invalid = java.util.Arrays.<org.springframework.security.core.Authentication>asList(
                null,
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("user", "password"),
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("wrong principal", null, List.of()));
        for (var authentication : invalid) {
            var createRequest = post("/api/clothes")
                    .contentType(MediaType.APPLICATION_JSON).content(createBody());
            var updateRequest = patch("/api/clothes/{clothesId}", clothesId)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새 이름\"}");
            if (authentication != null) {
                createRequest.principal(authentication);
                updateRequest.principal(authentication);
            }
            mvc.perform(createRequest)
                    .andExpect(status().isUnauthorized());
            mvc.perform(updateRequest)
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }

    @Test
    void rejectsOtherOwnerOnCreate() throws Exception {
        mvc.perform(post("/api/clothes").principal(auth(UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON).content(createBody()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void updatePassesAuthenticatedUserAndReturnsAttributes() throws Exception {
        var request = new com.otboo.team03.clothes.dto.request.ClothesUpdateRequest("새 이름", null, null);
        var attribute = new com.otboo.team03.clothes.dto.response.ClothesAttributeWithDefDto(
                definitionId, "색상", List.of("파랑"), "파랑");
        when(service.update(clothesId, request, ownerId)).thenReturn(
                new ClothesDto(clothesId, ownerId, "새 이름", null, ClothesType.TOP, List.of(attribute)));
        mvc.perform(patch("/api/clothes/{clothesId}", clothesId).principal(auth(ownerId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새 이름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes[0].value").value("파랑"));
        verify(service).update(clothesId, request, ownerId);
    }

    private org.springframework.security.core.Authentication auth(UUID userId) {
        var user = new com.otboo.team03.user.dto.response.UserDto(userId, java.time.Instant.now(),
                "test@example.com", "사용자", com.otboo.team03.user.entity.UserRole.USER, false);
        var details = new com.otboo.team03.global.security.OtbooUserDetails(user, "encoded");
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                details, null, details.getAuthorities());
    }

    private String createBody() {
        return "{\"ownerId\":\"%s\",\"name\":\"반팔\",\"type\":\"TOP\",\"attributes\":[{\"definitionId\":\"%s\",\"value\":\"파랑\"}]}"
                .formatted(ownerId, definitionId);
    }

    private ClothesDto response() {
        return new ClothesDto(clothesId, ownerId, "반팔", null, ClothesType.TOP, List.of());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "null", "[]", "[null]", "[{\"value\":\"파랑\"}]", "[{\"definitionId\":\"00000000-0000-0000-0000-000000000001\",\"value\":\" \"}]"
    })
    @DisplayName("속성 목록이나 항목의 필수 값이 유효하지 않으면 400으로 거절한다")
    void invalidAttributes(String attributes) throws Exception {
        mvc.perform(post("/api/clothes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerId\":\"" + ownerId + "\",\"name\":\"반팔\",\"type\":\"TOP\",\"attributes\":" + attributes + "}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
