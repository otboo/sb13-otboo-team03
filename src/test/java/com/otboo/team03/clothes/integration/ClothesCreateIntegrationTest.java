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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
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
    @DisplayName("현재 권한 제한: 비로그인 요청도 다른 소유자 ID로 생성된다")
    void currentSecurityGap() throws Exception {
        // 실제 Security 필터를 포함한다. 이 테스트는 현재의 권한 공백을 드러낸다.
        // 인증/소유자 검증을 구현할 때 이 기대값을 401/403 거절 검증으로 바꿔야 한다.
        mvc.perform(post("/api/clothes").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"ownerId":"%s","name":"반팔","type":"TOP",
                         "attributes":[{"definitionId":"%s","value":"파랑"}]}
                        """.formatted(owner.getId(), color.getId())))
                .andExpect(status().isCreated());
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

    private ClothesCreateRequest request() {
        return new ClothesCreateRequest(owner.getId(), "반팔", ClothesType.TOP,
                List.of(new ClothesAttributeDto(color.getId(), "파랑")));
    }
}
