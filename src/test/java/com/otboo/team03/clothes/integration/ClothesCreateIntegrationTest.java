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

    private ClothesCreateRequest request() {
        return new ClothesCreateRequest(owner.getId(), "반팔", ClothesType.TOP,
                List.of(new ClothesAttributeDto(color.getId(), "파랑")));
    }
}
