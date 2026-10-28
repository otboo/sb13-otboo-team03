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
        // given: Service가 성공했을 때 반환할 결과를 지정한다.
        when(service.create(any())).thenReturn(response());

        // when + then: 프론트처럼 JSON 요청을 보내 상태 코드와 응답 필드를 확인한다.
        mvc.perform(post("/api/clothes").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerId":"%s","name":"반팔","type":"TOP","attributes":[]}
                                """.formatted(ownerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(clothesId.toString()))
                .andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
                .andExpect(jsonPath("$.name").value("반팔"))
                .andExpect(jsonPath("$.type").value("TOP"));
        // JSON이 요청 DTO로 변환되어 Service에 전달됐는지도 검증한다.
        verify(service).create(new ClothesCreateRequest(ownerId, "반팔", ClothesType.TOP, List.of()));
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

    private ClothesDto response() {
        return new ClothesDto(clothesId, ownerId, "반팔", null, ClothesType.TOP, List.of());
    }
}
