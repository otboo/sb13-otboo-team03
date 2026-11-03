package com.otboo.team03.clothes.integration;

import com.otboo.team03.clothes.dto.request.*;
import com.otboo.team03.clothes.dto.response.ClothesDto;
import com.otboo.team03.clothes.entity.*;
import com.otboo.team03.clothes.repository.*;
import com.otboo.team03.clothes.service.ClothesService;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 PostgreSQL·Flyway·보안 필터·HTTP 응답을 함께 검증한다.
 * 의상 및 공유 정의 CRUD, 관리자/소유자 권한, CSRF, 삭제 후 타 도메인 데이터 보존을 확인한다.
 * 프런트 브라우저 연동이나 Redis를 통한 실제 로그인 세션 전체를 검증하는 테스트는 아니다.
 */
// 실제 운영 키 대신 테스트 전용 키를 사용한다. HS256은 최소 32바이트가 필요하다.
@SpringBootTest(properties = "otboo.jwt.secret=otboo-clothes-test-secret-12345678901234567890")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ClothesCreateIntegrationTest {
    // 개발 DB 대신 테스트 전용 PostgreSQL을 사용한다. 테스트가 끝나면 컨테이너를 제거한다.
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired ClothesService service;
    @Autowired ClothesRepository clothesRepository;
    @Autowired ClothesAttributeDefinitionRepository definitions;
    @Autowired UserRepository users;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoSpyBean ClothesAttributeRepository attributes;
    User owner;
    ClothesAttributeDefinition color;

    @BeforeEach
    void prepare() {
        owner = users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "encoded", "소유자"));
        color = definitions.saveAndFlush(new ClothesAttributeDefinition("색상", List.of("파랑", "빨강")));
    }

    @AfterEach
    void cleanUp() {
        // 외래 키 순서를 지켜 이 테스트 컨테이너에 저장한 데이터만 정리한다.
        attributes.deleteAllInBatch();
        clothesRepository.deleteAllInBatch();
        definitions.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    @DisplayName("실제 DB에 의상과 두 속성을 저장하고 응답에도 포함한다")
    void persistClothesAndAttributes() {
        ClothesAttributeDefinition material = definitions.saveAndFlush(
                new ClothesAttributeDefinition("소재", List.of("면")));
        ClothesDto result = service.create(new ClothesCreateRequest(owner.getId(), "반팔", ClothesType.TOP,
                List.of(new ClothesAttributeDto(color.getId(), "파랑"),
                        new ClothesAttributeDto(material.getId(), "면"))));

        assertThat(clothesRepository.findById(result.id())).isPresent();
        assertThat(attributes.findById(new ClothesAttribute.Key(result.id(), color.getId())))
                .get().extracting(ClothesAttribute::getValue).isEqualTo("파랑");
        assertThat(attributes.findById(new ClothesAttribute.Key(result.id(), material.getId())))
                .get().extracting(ClothesAttribute::getValue).isEqualTo("면");
        assertThat(result.attributes()).extracting(dto -> dto.value()).containsExactly("파랑", "면");
        // 현재 이미지는 처리하지 않으므로 null이다. 업로드 성공을 검증하는 테스트는 아니다.
        assertThat(result.imageUrl()).isNull();
        // 생성 트랜잭션이 끝난 뒤 다시 조회해도 모든 속성 정보가 반환되는지 확인한다.
        ClothesDto retrieved = service.findById(result.id());
        assertThat(retrieved.attributes()).containsExactlyInAnyOrderElementsOf(result.attributes());
    }

    @Test
    @DisplayName("의상 INSERT 후 속성의 DB 제약 오류가 나면 의상도 롤백된다")
    void rollbackWhenAttributeInsertFails() {
        // 테스트 자체에는 @Transactional을 붙이지 않는다.
        // 그래야 Service의 트랜잭션 종료 후 별도 조회로 롤백을 확인할 수 있다.
        doAnswer(invocation -> {
            clothesRepository.flush(); // 의상이 실제로 INSERT된 뒤 실패하도록 한다.
            ClothesAttribute attribute = invocation.getArgument(0);
            // Service 검증 이후 DB의 NOT NULL 제약 오류를 의도적으로 주입한다.
            // 같은 트랜잭션의 DB 연결에서 실제 SQL로 NOT NULL 제약 오류를 발생시킨다.
            jdbc.update("INSERT INTO clothes_attribute (clothes_id, definition_id, value) VALUES (?, ?, ?)",
                    attribute.getClothes().getId(), attribute.getDefinition().getId(), null);
            return attribute;
        }).when(attributes).save(any(ClothesAttribute.class));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(clothesRepository.count()).isZero();
        assertThat(attributes.count()).isZero();
        assertThat(users.existsById(owner.getId())).isTrue();
        assertThat(definitions.existsById(color.getId())).isTrue();
    }

    @Test
    @DisplayName("비로그인 사용자는 의상을 생성할 수 없다")
    void unauthenticatedUserCannotCreateClothes() throws Exception {
        mvc.perform(post("/api/clothes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"ownerId":"%s","name":"반팔","type":"TOP",
                     "attributes":[{"definitionId":"%s","value":"파랑"}]}
                    """.formatted(owner.getId(), color.getId())))
                .andExpect(status().isUnauthorized());

        assertThat(clothesRepository.count()).isZero();
    }

    @Test
    @DisplayName("사용자별 목록은 다른 사용자 의상을 제외하고 속성을 일괄 조회한다")
    void listByOwnerWithBoundedQueries() {
        ClothesDto first = service.create(request());
        ClothesDto second = service.create(request());
        User other = users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "encoded", "다른 사용자"));
        service.create(new ClothesCreateRequest(other.getId(), "다른 의상", ClothesType.TOP,
                List.of(new ClothesAttributeDto(color.getId(), "빨강"))));

        // Hibernate 통계로 실제 SELECT 수가 의상 수만큼 늘어나지 않는지 확인한다.
        org.hibernate.stat.Statistics statistics = entityManagerFactory
                .unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            List<ClothesDto> result = service.findAllByOwnerId(owner.getId());
            assertThat(result).extracting(ClothesDto::id).containsExactlyInAnyOrder(first.id(), second.id());
            assertThat(result).allSatisfy(dto -> {
                assertThat(dto.ownerId()).isEqualTo(owner.getId());
                assertThat(dto.attributes()).containsExactlyElementsOf(first.attributes());
            });
            // 의상 목록 SELECT 1회 + 속성과 정의의 JOIN SELECT 1회.
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    @DisplayName("같은 이름의 의상도 커서로 중복 없이 이어 조회하고 타입 필터를 적용한다")
    void cursorPagesWithSameNameAndFilter() {
        ClothesDto first = service.create(request());
        ClothesDto second = service.create(request());
        service.create(new ClothesCreateRequest(owner.getId(), "바지", ClothesType.BOTTOM,
                List.of(new ClothesAttributeDto(color.getId(), "파랑"))));
        var page1 = service.findAll(owner.getId(), 1, ClothesType.TOP, null, null);
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.totalCount()).isEqualTo(2);
        assertThat(page1.data().get(0).attributes()).isNotEmpty();
        var page2 = service.findAll(owner.getId(), 1, ClothesType.TOP,
                page1.nextCursor(), page1.nextIdAfter());
        assertThat(page2.hasNext()).isFalse();
        assertThat(page2.nextCursor()).isNull();
        assertThat(page2.nextIdAfter()).isNull();
        assertThat(page2.totalCount()).isEqualTo(2);
        assertThat(java.util.stream.Stream.concat(page1.data().stream(), page2.data().stream())
                .map(ClothesDto::id).toList()).containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(service.findAll(UUID.randomUUID(), 10, null, null, null).data()).isEmpty();
        assertThatThrownBy(() -> service.findAll(owner.getId(), 0, null, null, null))
                .isInstanceOf(com.otboo.team03.global.exception.OtbooException.class);
        assertThatThrownBy(() -> service.findAll(owner.getId(), 1, null, "반팔", null))
                .isInstanceOf(com.otboo.team03.global.exception.OtbooException.class);
    }

    @Test
    @DisplayName("관리자는 속성 정의를 등록하고 저장된 생성 시각을 받는다")
    void adminCreatesDefinition() throws Exception {
        long before = definitions.count();
        String response = mvc.perform(post("/api/clothes/attribute-defs").with(csrf()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"소재\",\"selectableValues\":[\"면\",\"울\"]}"))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.id").isNotEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(definitions.count()).isEqualTo(before + 1);
        var saved = definitions.findAll().stream().filter(d -> d.getName().equals("소재")).findFirst().orElseThrow();
        assertThat(saved.getSelectableValues()).containsExactly("면", "울");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(response).contains(saved.getId().toString());
    }

    @Test
    @DisplayName("일반 사용자는 속성 정의를 등록할 수 없다")
    void userCannotCreateDefinition() throws Exception {
        long before = definitions.count();
        mvc.perform(post("/api/clothes/attribute-defs").with(csrf()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("user").roles("USER"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"소재\",\"selectableValues\":[\"면\"]}"))
                .andExpect(status().isForbidden());
        assertThat(definitions.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("비로그인 사용자는 속성 정의를 등록할 수 없다")
    void anonymousCannotCreateDefinition() throws Exception {
        long before = definitions.count();
        mvc.perform(post("/api/clothes/attribute-defs").with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"소재\",\"selectableValues\":[\"면\"]}"))
                .andExpect(status().isUnauthorized());
        assertThat(definitions.count()).isEqualTo(before);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "{\"name\":\" \",\"selectableValues\":[\"면\"]}",
            "{\"name\":\"소재\",\"selectableValues\":[]}",
            "{\"name\":\"소재\",\"selectableValues\":[null]}",
            "{\"name\":\"소재\",\"selectableValues\":[\" \"]}"
    })
    @DisplayName("관리자라도 잘못된 정의 입력은 저장하지 않는다")
    void invalidDefinitionIsRejected(String body) throws Exception {
        long before = definitions.count();
        mvc.perform(post("/api/clothes/attribute-defs").with(csrf()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(definitions.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("속성 교체는 값 변경·추가·제거를 DB와 응답에 반영하고 생략하면 유지한다")
    void replaceClothesAttributes() {
        ClothesDto created = service.create(request());
        var material = definitions.saveAndFlush(new ClothesAttributeDefinition("소재", List.of("면")));
        var response = service.update(created.id(), new ClothesUpdateRequest("새 이름", null,
                List.of(new ClothesAttributeDto(color.getId(), "빨강"),
                        new ClothesAttributeDto(material.getId(), "면"))), owner.getId());
        assertThat(response.attributes()).extracting(a -> a.value()).containsExactly("빨강", "면");
        assertThat(service.findById(created.id()).attributes()).hasSize(2);

        service.update(created.id(), new ClothesUpdateRequest(null, null,
                List.of(new ClothesAttributeDto(material.getId(), "면"))), owner.getId());
        assertThat(service.findById(created.id()).attributes()).extracting(a -> a.definitionId())
                .containsExactly(material.getId());
        var unchanged = service.update(created.id(), new ClothesUpdateRequest("이름만 변경", null, null), owner.getId());
        assertThat(unchanged.attributes()).hasSize(1);

        var cleared = service.update(created.id(), new ClothesUpdateRequest(null, null, List.of()), owner.getId());
        assertThat(cleared.attributes()).isEmpty();
        assertThat(service.findById(created.id()).attributes()).isEmpty();
        assertThat(clothesRepository.existsById(created.id())).isTrue();
        assertThat(definitions.existsById(color.getId())).isTrue();
        assertThat(definitions.existsById(material.getId())).isTrue();
    }

    @Test
    @DisplayName("잘못된 선택값·중복·없는 정의·타인 수정은 기존 이름과 속성을 유지한다")
    void invalidAttributeUpdateDoesNotChangeStoredData() {
        ClothesDto created = service.create(request());
        List<List<ClothesAttributeDto>> invalidLists = List.of(
                List.of(new ClothesAttributeDto(color.getId(), "허용되지않음")),
                List.of(new ClothesAttributeDto(color.getId(), "빨강"), new ClothesAttributeDto(color.getId(), "파랑")),
                List.of(new ClothesAttributeDto(UUID.randomUUID(), "파랑")),
                java.util.Arrays.asList((ClothesAttributeDto) null));
        for (var invalid : invalidLists) {
            assertThatThrownBy(() -> service.update(created.id(),
                    new ClothesUpdateRequest("바뀌면안됨", null, invalid), owner.getId()))
                    .isInstanceOf(com.otboo.team03.global.exception.OtbooException.class);
            assertThat(service.findById(created.id())).isEqualTo(created);
        }
        assertThatThrownBy(() -> service.update(created.id(),
                new ClothesUpdateRequest("타인수정", null, List.of()), UUID.randomUUID()))
                .isInstanceOf(com.otboo.team03.global.exception.OtbooException.class);
        assertThat(service.findById(created.id())).isEqualTo(created);
    }

    @Test
    @DisplayName("인증 사용자는 정의를 조회하고 관리자의 CSRF 없는 등록은 거부한다")
    void definitionListAndCsrfProtection() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/clothes/attribute-defs").param("sortBy", "name").param("sortDirection", "ASCENDING")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].id").value(color.getId().toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].selectableValues[0]").value("파랑"));
        long before = definitions.count();
        mvc.perform(post("/api/clothes/attribute-defs")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"소재\",\"selectableValues\":[\"면\"]}"))
                .andExpect(status().isForbidden());
        assertThat(definitions.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("정의 조회는 이름·생성시각 양방향 정렬과 부분 검색을 적용한다")
    void definitionSearchAndSort() throws Exception {
        var first = definitions.saveAndFlush(new ClothesAttributeDefinition("alpha fabric", List.of("면")));
        var second = definitions.saveAndFlush(new ClothesAttributeDefinition("beta fabric", List.of("울")));
        jdbc.update("UPDATE clothes_attribute_definitions SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(java.time.Instant.parse("2020-01-01T00:00:00Z")), first.getId());
        jdbc.update("UPDATE clothes_attribute_definitions SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(java.time.Instant.parse("2021-01-01T00:00:00Z")), second.getId());
        for (String field : List.of("name", "createdAt")) {
            for (String direction : List.of("ASCENDING", "DESCENDING")) {
                mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/clothes/attribute-defs")
                        .param("sortBy", field).param("sortDirection", direction).param("keywordLike", "FABRIC")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("user")))
                        .andExpect(status().isOk())
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()").value(2))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].id")
                                .value((direction.equals("ASCENDING") ? first : second).getId().toString()));
            }
        }
        assertThat(definitionService.findAll("name", "ASCENDING", "no-match")).isEmpty();
        // LIKE의 %와 _를 와일드카드가 아닌 검색 문자로 취급한다.
        assertThat(definitionService.findAll("name", "ASCENDING", "%")).isEmpty();
    }

    @Autowired com.otboo.team03.clothes.service.ClothesAttributeDefinitionService definitionService;
    @Test
    @DisplayName("정의 조회의 필수 정렬 누락과 잘못된 허용값은 400을 반환한다")
    void invalidDefinitionSort() throws Exception {
        for (String[] params : new String[][]{{null, "ASCENDING"}, {"name", null},
                {"invalid", "ASCENDING"}, {"name", "invalid"}}) {
            var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/clothes/attribute-defs")
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("user"));
            if (params[0] != null) request.param("sortBy", params[0]);
            if (params[1] != null) request.param("sortDirection", params[1]);
            mvc.perform(request).andExpect(status().isBadRequest())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.exceptionName").value("INVALID_INPUT_VALUE"));
        }
    }

    @Test
    @DisplayName("관리자는 미사용 정의만 삭제하고 사용 중인 정의와 의상 데이터는 보존한다")
    void deleteDefinitionLifecycle() throws Exception {
        var unused = definitions.saveAndFlush(new ClothesAttributeDefinition("미사용", List.of("값")));
        mvc.perform(definitionDelete(unused.getId(), "ADMIN", true))
                .andExpect(status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
        assertThat(definitions.existsById(unused.getId())).isFalse();
        mvc.perform(definitionDelete(unused.getId(), "ADMIN", true))
                .andExpect(status().isNotFound());

        var clothes = service.create(request());
        mvc.perform(definitionDelete(color.getId(), "ADMIN", true))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.exceptionName")
                        .value("ATTRIBUTE_DEFINITION_IN_USE"));
        assertThat(definitions.existsById(color.getId())).isTrue();
        assertThat(service.findById(clothes.id())).isEqualTo(clothes);
    }

    @Test
    @DisplayName("정의 삭제는 일반 사용자·비로그인·CSRF 누락 요청을 거부한다")
    void deleteDefinitionAuthorization() throws Exception {
        mvc.perform(definitionDelete(color.getId(), "USER", true)).andExpect(status().isForbidden());
        mvc.perform(definitionDelete(color.getId(), null, true)).andExpect(status().isUnauthorized());
        mvc.perform(definitionDelete(color.getId(), "ADMIN", false)).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/clothes/attribute-defs/invalid").with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        assertThat(definitions.existsById(color.getId())).isTrue();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder definitionDelete(
            UUID id, String role, boolean csrfPresent) {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/clothes/attribute-defs/{definitionId}", id);
        if (role != null) request.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("tester").roles(role));
        if (csrfPresent) request.with(csrf());
        return request;
    }

    @Test
    @DisplayName("관리자는 정의 이름·선택지를 수정하고 사용 중인 선택값은 제거할 수 없다")
    void updateDefinitionLifecycle() throws Exception {
        var createdAt = definitions.findById(color.getId()).orElseThrow().getCreatedAt();
        var clothes = service.create(request());
        mvc.perform(definitionPatch(color.getId(), "ADMIN", true,
                "{\"name\":\"의상 색상\",\"selectableValues\":[\"파랑\",\"빨강\",\"검정\"]}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.name").value("의상 색상"));
        // 사용하지 않는 빨강·검정은 제거 가능하며 이름을 생략하면 유지된다.
        mvc.perform(definitionPatch(color.getId(), "ADMIN", true, "{\"selectableValues\":[\"파랑\"]}"))
                .andExpect(status().isOk());
        mvc.perform(definitionPatch(color.getId(), "ADMIN", true, "{}"))
                .andExpect(status().isOk());
        mvc.perform(definitionPatch(color.getId(), "ADMIN", true,
                "{\"name\":\"변경되면안됨\",\"selectableValues\":[\"빨강\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.exceptionName").value("ATTRIBUTE_VALUE_IN_USE"));
        var stored = definitions.findById(color.getId()).orElseThrow();
        assertThat(stored.getName()).isEqualTo("의상 색상");
        assertThat(stored.getSelectableValues()).containsExactly("파랑");
        assertThat(stored.getCreatedAt()).isEqualTo(createdAt);
        assertThat(service.findById(clothes.id()).attributes().get(0).value()).isEqualTo("파랑");
        mvc.perform(definitionPatch(UUID.randomUUID(), "ADMIN", true, "{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("정의 수정은 권한·CSRF·입력값을 검증한다")
    void updateDefinitionValidationAndAuthorization() throws Exception {
        mvc.perform(definitionPatch(color.getId(), "USER", true, "{}"))
                .andExpect(status().isForbidden());
        mvc.perform(definitionPatch(color.getId(), null, true, "{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(definitionPatch(color.getId(), "ADMIN", false, "{}"))
                .andExpect(status().isForbidden());
        for (String body : List.of("{\"name\":\" \"}", "{\"selectableValues\":[]}",
                "{\"selectableValues\":[null]}", "{\"selectableValues\":[\" \"]}")) {
            mvc.perform(definitionPatch(color.getId(), "ADMIN", true, body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(definitions.findById(color.getId()).orElseThrow().getName()).isEqualTo("색상");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder definitionPatch(
            UUID id, String role, boolean csrfPresent, String body) {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/clothes/attribute-defs/{definitionId}", id).contentType(MediaType.APPLICATION_JSON).content(body);
        if (role != null) request.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("tester").roles(role));
        if (csrfPresent) request.with(csrf());
        return request;
    }

    @Test
    @DisplayName("의상 삭제는 여러 피드의 연결·속성값만 정리하고 피드·정의·다른 의상을 보존한다")
    void deleteClothesCascadesOnlyDependentRows() throws Exception {
        var target = service.create(request());
        var other = service.create(request());
        UUID weatherId = UUID.randomUUID();
        UUID feed1 = UUID.randomUUID();
        UUID feed2 = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO weathers (id, latitude, longitude, forecasted_at, forecast_at,
                sky_status, precipitation_type, precipitation_amount, precipitation_probability,
                temperature, humidity, wind_speed, created_at, updated_at)
                VALUES (?, 0, 0, now(), now(), 'CLEAR', 'NONE', 0, 0, 20, 50, 1, now(), now())
                """, weatherId);
        try {
            for (UUID feedId : List.of(feed1, feed2)) {
                jdbc.update("INSERT INTO feeds (id, author_id, weather_id, content, created_at, updated_at) VALUES (?, ?, ?, '피드 유지', now(), now())",
                        feedId, owner.getId(), weatherId);
                jdbc.update("INSERT INTO feed_clothes (feed_id, clothes_id) VALUES (?, ?)", feedId, target.id());
            }
            jdbc.update("INSERT INTO feed_clothes (feed_id, clothes_id) VALUES (?, ?)", feed1, other.id());
            mvc.perform(clothesDelete(target.id(), owner.getId(), true))
                    .andExpect(status().isNoContent())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
            assertThat(clothesRepository.existsById(target.id())).isFalse();
            assertThat(attributes.findAllByClothes_Id(target.id())).isEmpty();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM feed_clothes WHERE clothes_id = ?", Long.class, target.id())).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM feeds WHERE id IN (?, ?)", Long.class, feed1, feed2)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM feed_clothes WHERE clothes_id = ?", Long.class, other.id())).isEqualTo(1);
            assertThat(service.findById(other.id())).isEqualTo(other);
            assertThat(definitions.existsById(color.getId())).isTrue();
            assertThat(users.existsById(owner.getId())).isTrue();
            mvc.perform(clothesDelete(target.id(), owner.getId(), true)).andExpect(status().isNotFound());
        } finally {
            jdbc.update("DELETE FROM feed_clothes WHERE feed_id IN (?, ?)", feed1, feed2);
            jdbc.update("DELETE FROM feeds WHERE id IN (?, ?)", feed1, feed2);
            jdbc.update("DELETE FROM weathers WHERE id = ?", weatherId);
        }
    }

    @Test
    @DisplayName("타인·비로그인·CSRF 누락 삭제 요청은 의상을 보존한다")
    void deleteClothesAuthorization() throws Exception {
        var target = service.create(request());
        mvc.perform(clothesDelete(target.id(), UUID.randomUUID(), true)).andExpect(status().isForbidden());
        mvc.perform(clothesDelete(target.id(), null, true)).andExpect(status().isUnauthorized());
        mvc.perform(clothesDelete(target.id(), owner.getId(), false)).andExpect(status().isForbidden());
        assertThat(service.findById(target.id())).isEqualTo(target);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder clothesDelete(
            UUID id, UUID requesterId, boolean csrfPresent) {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/clothes/{clothesId}", id);
        if (requesterId != null) {
            var dto = new com.otboo.team03.user.dto.response.UserDto(requesterId, java.time.Instant.now(),
                    "test@example.com", "테스트", com.otboo.team03.user.entity.UserRole.USER, false);
            request.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(
                    new com.otboo.team03.global.security.OtbooUserDetails(dto, "encoded")));
        }
        if (csrfPresent) request.with(csrf());
        return request;
    }

    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "create-update", "create-delete", "update-update", "update-delete", "change-update", "change-delete"
    })
    @DisplayName("동시 의상 등록·수정과 정의 변경은 DB 잠금으로 순서를 보장한다")
    void concurrentDefinitionUsageAndChange(String scenario) throws Exception {
        boolean delete = scenario.endsWith("delete");
        boolean changeFirst = scenario.startsWith("change");
        boolean updateClothes = scenario.startsWith("update");
        var existing = updateClothes ? service.create(request()) : null;
        String usedValue = updateClothes ? "빨강" : "파랑";
        String retainedValue = updateClothes ? "파랑" : "빨강";
        Runnable use = () -> {
            if (updateClothes) {
                service.update(existing.id(), new ClothesUpdateRequest(null, null,
                        List.of(new ClothesAttributeDto(color.getId(), usedValue))), owner.getId());
            } else {
                service.create(request());
            }
        };
        Runnable change = () -> {
            if (delete) definitionService.delete(color.getId());
            else definitionService.update(color.getId(),
                    new ClothesAttributeDefUpdateRequest(null, List.of(retainedValue)));
        };
        var expected = changeFirst
                ? (delete ? com.otboo.team03.global.exception.ErrorCode.NOT_FOUND_ATTRIBUTE_DEFINITION
                          : com.otboo.team03.global.exception.ErrorCode.INVALID_CLOTHES_ATTRIBUTE_VALUE)
                : (delete ? com.otboo.team03.global.exception.ErrorCode.ATTRIBUTE_DEFINITION_IN_USE
                          : com.otboo.team03.global.exception.ErrorCode.ATTRIBUTE_VALUE_IN_USE);
        runContendingTransactions(changeFirst ? change : use, changeFirst ? use : change, expected);
        if (!changeFirst) {
            assertThat(definitions.findById(color.getId()).orElseThrow().getSelectableValues())
                    .contains(usedValue);
            assertThat(attributes.existsByDefinition_Id(color.getId())).isTrue();
        } else {
            assertThat(attributes.existsByDefinition_Id(color.getId())).isFalse();
            assertThat(clothesRepository.findAllByOwner_Id(owner.getId())).isEmpty();
        }
    }

    /** 두 독립 트랜잭션을 실제로 경합시킨다. 시간 지연 추측 대신 PostgreSQL의 Lock 대기를 확인한다. */
    private void runContendingTransactions(Runnable first, Runnable second,
            com.otboo.team03.global.exception.ErrorCode expected) throws Exception {
        runContendingTransactions(first, second, expected, "clothes_attribute_definitions");
    }

    private void runContendingTransactions(Runnable first, Runnable second,
            com.otboo.team03.global.exception.ErrorCode expected, String table) throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        try {
            var holder = pool.submit(() -> transaction.executeWithoutResult(status -> {
                first.run();
                ready.countDown();
                try {
                    if (!release.await(15, java.util.concurrent.TimeUnit.SECONDS))
                        throw new IllegalStateException("잠금 해제 대기 시간 초과");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var contender = pool.submit(second);
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            boolean blocked = false;
            while (System.nanoTime() < deadline && !contender.isDone()) {
                blocked = Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM pg_stat_activity
                        WHERE datname = current_database() AND wait_event_type = 'Lock'
                        AND query LIKE ?)
                        """, Boolean.class, "%" + table + "%"));
                if (blocked) break;
                Thread.sleep(20);
            }
            assertThat(blocked).as("두 번째 요청이 실제 DB 잠금을 기다리는지").isTrue();
            release.countDown();
            holder.get(10, java.util.concurrent.TimeUnit.SECONDS);
            if (expected == null) {
                contender.get(10, java.util.concurrent.TimeUnit.SECONDS);
                return;
            }
            assertThatThrownBy(() -> contender.get(10, java.util.concurrent.TimeUnit.SECONDS))
                    .isInstanceOfSatisfying(java.util.concurrent.ExecutionException.class, ex -> {
                        assertThat(ex.getCause()).isInstanceOfSatisfying(
                                com.otboo.team03.global.exception.OtbooException.class,
                                cause -> assertThat(cause.getErrorCode()).isEqualTo(expected));
                    });
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("V4 이후 기존 URL은 유지하고 이미지 key를 별도로 저장·교체·제거한다")
    void persistImageKeySeparatelyFromLegacyUrl() {
        var clothes = clothesRepository.saveAndFlush(new Clothes("이미지 의상", ClothesType.TOP,
                "https://example.com/legacy.png", owner));
        var id = clothes.getId();
        assertThat(clothesRepository.findById(id).orElseThrow().getImageKey()).isNull();

        clothes.updateImageKey("clothes/first.png");
        clothesRepository.saveAndFlush(clothes);
        var reloaded = clothesRepository.findById(id).orElseThrow();
        assertThat(reloaded.getImageKey()).isEqualTo("clothes/first.png");
        assertThat(reloaded.getImageUrl()).isEqualTo("https://example.com/legacy.png");
        assertThat(jdbc.queryForObject("SELECT image_key FROM clothes WHERE id = ?", String.class, id))
                .isEqualTo("clothes/first.png");

        reloaded.updateImageKey("clothes/replacement.png");
        clothesRepository.saveAndFlush(reloaded);
        assertThat(clothesRepository.findById(id).orElseThrow().getImageKey()).isEqualTo("clothes/replacement.png");
        reloaded.updateImageKey(null);
        clothesRepository.saveAndFlush(reloaded);
        assertThat(clothesRepository.findById(id).orElseThrow().getImageKey()).isNull();
    }

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.otboo.team03.global.storage.ImageStorage imageStorage;

    @Test
    @DisplayName("multipart 생성·미첨부 수정·이미지 교체·삭제는 key와 서명 URL을 연결한다")
    void multipartImageLifecycle() throws Exception {
        var image = new org.springframework.mock.web.MockMultipartFile("image", "test.png", "image/png", new byte[]{1});
        org.mockito.Mockito.when(imageStorage.upload(any())).thenReturn("clothes/first.png", "clothes/second.png");
        org.mockito.Mockito.when(imageStorage.getAccessUrl("clothes/first.png")).thenReturn("https://example.com/signed-first");
        org.mockito.Mockito.when(imageStorage.getAccessUrl("clothes/second.png")).thenReturn("https://example.com/signed-second");
        String body = "{\"ownerId\":\"%s\",\"name\":\"반팔\",\"type\":\"TOP\",\"attributes\":[{\"definitionId\":\"%s\",\"value\":\"파랑\"}]}"
                .formatted(owner.getId(), color.getId());
        mvc.perform(imageMultipart(org.springframework.http.HttpMethod.POST, "/api/clothes", body).file(image))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.imageUrl").value("https://example.com/signed-first"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.imageKey").doesNotExist());
        var clothes = clothesRepository.findAllByOwner_Id(owner.getId()).get(0);
        assertThat(clothes.getImageKey()).isEqualTo("clothes/first.png");
        assertThat(clothes.getImageUrl()).isNull();
        mvc.perform(imageMultipart(org.springframework.http.HttpMethod.PATCH, "/api/clothes/" + clothes.getId(), "{\"name\":\"이름만 수정\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.imageUrl").value("https://example.com/signed-first"));
        org.mockito.Mockito.verify(imageStorage, org.mockito.Mockito.times(1)).upload(any());
        org.mockito.Mockito.verify(imageStorage, org.mockito.Mockito.never()).delete(any());
        mvc.perform(imageMultipart(org.springframework.http.HttpMethod.PATCH, "/api/clothes/" + clothes.getId(), "{}").file(image))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.imageUrl").value("https://example.com/signed-second"));
        assertThat(clothesRepository.findById(clothes.getId()).orElseThrow().getImageKey()).isEqualTo("clothes/second.png");
        org.mockito.Mockito.verify(imageStorage).delete("clothes/first.png");
        assertThat(service.findById(clothes.getId()).imageUrl()).isEqualTo("https://example.com/signed-second");
        assertThat(service.findAll(owner.getId(), 20, null, null, null).data().get(0).imageUrl()).isEqualTo("https://example.com/signed-second");
        mvc.perform(clothesDelete(clothes.getId(), owner.getId(), true)).andExpect(status().isNoContent());
        org.mockito.Mockito.verify(imageStorage).delete("clothes/second.png");
    }

    @Test
    @DisplayName("DB 롤백 시 신규 이미지만 정리하고 기존 이미지 key와 파일은 유지한다")
    void imageRollbackPreservesOldFile() {
        var created = service.create(request());
        var clothes = clothesRepository.findById(created.id()).orElseThrow();
        clothes.updateImageKey("clothes/old.png");
        clothesRepository.saveAndFlush(clothes);
        org.mockito.Mockito.when(imageStorage.upload(any())).thenReturn("clothes/new.png");
        org.mockito.Mockito.when(imageStorage.getAccessUrl("clothes/new.png")).thenReturn("https://example.com/new");
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            service.update(created.id(), new ClothesUpdateRequest("롤백", null, null), owner.getId(),
                    new org.springframework.mock.web.MockMultipartFile("image", new byte[]{1}));
            org.mockito.Mockito.verify(imageStorage, org.mockito.Mockito.never()).delete(any());
            status.setRollbackOnly();
        });
        assertThat(clothesRepository.findById(created.id()).orElseThrow().getImageKey()).isEqualTo("clothes/old.png");
        org.mockito.Mockito.verify(imageStorage).delete("clothes/new.png");
        org.mockito.Mockito.verify(imageStorage, org.mockito.Mockito.never()).delete("clothes/old.png");
    }

    @Test
    @DisplayName("업로드 후 DB 저장 오류가 나면 의상과 신규 파일을 함께 정리한다")
    void imageCreateDatabaseFailureCleansFile() {
        org.mockito.Mockito.when(imageStorage.upload(any())).thenReturn("clothes/rollback.png");
        doAnswer(invocation -> { throw new DataIntegrityViolationException("attribute insert failed"); })
                .when(attributes).save(any(ClothesAttribute.class));
        assertThatThrownBy(() -> service.create(request(), new org.springframework.mock.web.MockMultipartFile("image", new byte[]{1})))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(clothesRepository.findAllByOwner_Id(owner.getId())).isEmpty();
        org.mockito.Mockito.verify(imageStorage).delete("clothes/rollback.png");
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder imageMultipart(
            org.springframework.http.HttpMethod method, String url, String json) {
        var dto = com.otboo.team03.user.dto.response.UserDto.from(owner);
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(method, url);
        request.file(new org.springframework.mock.web.MockMultipartFile("request", "", "application/json",
                json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        request.with(csrf()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(
                new com.otboo.team03.global.security.OtbooUserDetails(dto, "encoded")));
        return request;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "image-name", "name-image", "image-image", "image-delete", "delete-image"
    })
    @DisplayName("동시 이미지 교체·이름 수정·삭제는 최신 key를 유지하고 삭제된 key를 복원하지 않는다")
    void concurrentImageChangesPreserveLiveKey(String scenario) throws Exception {
        var created = service.create(request());
        var clothes = clothesRepository.findById(created.id()).orElseThrow();
        clothes.updateImageKey("clothes/old.png");
        clothesRepository.saveAndFlush(clothes);
        // S3는 대체하되 파일 생성·삭제 상태를 추적하여 DB key가 살아 있는 파일을 가리키는지 확인한다.
        var files = java.util.concurrent.ConcurrentHashMap.<String>newKeySet();
        files.add("clothes/old.png");
        var sequence = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.when(imageStorage.upload(any())).thenAnswer(invocation -> {
            String key = "clothes/new-" + sequence.incrementAndGet() + ".png";
            files.add(key);
            return key;
        });
        org.mockito.Mockito.when(imageStorage.getAccessUrl(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> "https://example.com/" + invocation.getArgument(0));
        doAnswer(invocation -> { files.remove((String) invocation.getArgument(0)); return null; })
                .when(imageStorage).delete(org.mockito.ArgumentMatchers.anyString());
        Runnable image = () -> service.update(created.id(), new ClothesUpdateRequest(null, null, null), owner.getId(),
                new org.springframework.mock.web.MockMultipartFile("image", new byte[]{1}));
        Runnable name = () -> service.update(created.id(), new ClothesUpdateRequest("변경된 이름", null, null), owner.getId());
        Runnable delete = () -> service.delete(created.id(), owner.getId());
        String[] operations = scenario.split("-");
        var actions = java.util.Map.of("image", image, "name", name, "delete", delete);
        var expected = operations[0].equals("delete")
                ? com.otboo.team03.global.exception.ErrorCode.NOT_FOUND_CLOTHES : null;
        runContendingTransactions(actions.get(operations[0]), actions.get(operations[1]), expected, "clothes");

        var result = clothesRepository.findById(created.id());
        if (scenario.contains("delete")) {
            assertThat(result).isEmpty();
            assertThat(files).isEmpty();
        } else {
            var saved = result.orElseThrow();
            assertThat(saved.getImageKey()).startsWith("clothes/new-");
            assertThat(files).containsExactly(saved.getImageKey());
            if (scenario.contains("name")) assertThat(saved.getName()).isEqualTo("변경된 이름");
            if (scenario.equals("image-image")) assertThat(saved.getImageKey()).isEqualTo("clothes/new-2.png");
        }
    }

    private ClothesCreateRequest request() {
        return new ClothesCreateRequest(owner.getId(), "반팔", ClothesType.TOP,
                List.of(new ClothesAttributeDto(color.getId(), "파랑")));
    }
}
