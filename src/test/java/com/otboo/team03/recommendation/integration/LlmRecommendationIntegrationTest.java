package com.otboo.team03.recommendation.integration;

import com.otboo.team03.clothes.entity.*;
import com.otboo.team03.clothes.repository.*;
import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.global.security.jwt.JwtTokenProvider;
import com.otboo.team03.global.storage.ImageStorage;
import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.profile.repository.ProfileRepository;
import com.otboo.team03.recommendation.client.GroqOutfitClient;
import com.otboo.team03.recommendation.client.dto.LlmOutfitDecision;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import com.otboo.team03.recommendation.repository.RedisRecommendationHistoryRepository;
import com.otboo.team03.support.PostgresTestSupport;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import com.otboo.team03.weather.entity.*;
import com.otboo.team03.weather.repository.WeatherRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** DB를 커밋한 뒤 HTTP 요청을 실행하여 LLM 호출 시 트랜잭션 종료와 지연 로딩도 확인한다. */
@SpringBootTest(properties = {"otboo.recommendation.llm.enabled=true", "groq.api-key=test-key"})
@AutoConfigureMockMvc
@Testcontainers
class LlmRecommendationIntegrationTest extends PostgresTestSupport {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private ProfileRepository profiles;
    @Autowired private ClothesRepository clothes;
    @Autowired private ClothesAttributeDefinitionRepository definitions;
    @Autowired private ClothesAttributeRepository attributes;
    @Autowired private WeatherRepository weathers;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JwtTokenProvider tokenProvider;
    @Autowired private RedisRecommendationHistoryRepository history;
    @MockitoBean private JwtRegistry jwtRegistry;
    @MockitoBean private ImageStorage imageStorage;
    @MockitoBean private GroqOutfitClient client;
    private Fixture cleanupFixture;

    @AfterEach
    void cleanCommittedFixture() {
        if (cleanupFixture == null) {
            return;
        }
        Fixture fixture = cleanupFixture;
        history.delete(fixture.ownerId(), fixture.weatherId());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List<UUID> ids = List.of(fixture.topId(), fixture.bottomId(), fixture.dressId(), fixture.otherDressId());
            attributes.deleteAll(attributes.findAllByClothes_IdIn(ids));
            attributes.flush();
            clothes.deleteAllById(ids);
            clothes.flush();
            definitions.deleteById(fixture.definitionId());
            profiles.deleteById(fixture.ownerId());
            profiles.flush();
            users.deleteAllById(List.of(fixture.ownerId(), fixture.otherOwnerId()));
            weathers.deleteById(fixture.weatherId());
        });
    }

    @Test
    void 사전에_없는_제목과_속성으로_기존_추천과_다른_원피스를_선택하고_최종_결과만_기록한다() throws Exception {
        Fixture fixture = fixture();
        when(client.serializeInput(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            LlmRecommendationInput input = invocation.getArgument(0);
            assertThat(input.wardrobe()).hasSize(3); // 다른 사용자 의상은 포함되지 않는다.
            assertThat(input.wardrobe()).anySatisfy(item -> {
                assertThat(item.clothesId()).isEqualTo(fixture.dressId());
                assertThat(item.name()).isEqualTo("시스루 크롭 원피스");
                assertThat(item.attributes()).contains(new LlmRecommendationInput.Attribute("원단 특징", "비침 있음"));
            });
            assertThat(input.temperatureSensitivity()).isEqualTo(3);
            assertThat(input.weather().precipitationType()).isEqualTo(PrecipitationType.RAIN);
            return "input";
        });
        when(client.recommend(any(), eq("input"))).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new LlmOutfitDecision(List.of(fixture.dressId()), List.of(
                    new LlmOutfitDecision.Analysis(fixture.dressId(), List.of(
                            new LlmOutfitDecision.Feature("비침", "있음", "비침 있음", LlmOutfitDecision.Source.ATTRIBUTE),
                            new LlmOutfitDecision.Feature("기장", "크롭", "크롭", LlmOutfitDecision.Source.TITLE)))
            ), "제목과 원단 특징을 고려한 코디입니다.");
        });

        mvc.perform(get("/api/recommendations").header("Authorization", "Bearer " + token(fixture.ownerId()))
                        .param("weatherId", fixture.weatherId().toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.clothes.length()").value(1))
                .andExpect(jsonPath("$.clothes[0].clothesId").value(fixture.dressId().toString()))
                .andExpect(jsonPath("$.clothes[0].attributes[0].definitionName").value("원단 특징"));
        assertThat(history.find(fixture.ownerId(), fixture.weatherId())).containsExactly(fixture.dressId());
        verify(client, times(1)).recommend(any(), anyString());
    }

    @Test
    void 통신_실패시_기존_상의하의_추천을_응답하고_그_결과를_기록한다() throws Exception {
        Fixture fixture = fixture();
        when(client.serializeInput(any())).thenReturn("input");
        when(client.recommend(any(), anyString())).thenThrow(new ResourceAccessException("timeout"));

        mvc.perform(get("/api/recommendations").header("Authorization", "Bearer " + token(fixture.ownerId()))
                        .param("weatherId", fixture.weatherId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clothes.length()").value(2))
                .andExpect(jsonPath("$.clothes[0].clothesId").value(fixture.topId().toString()))
                .andExpect(jsonPath("$.clothes[1].clothesId").value(fixture.bottomId().toString()));
        assertThat(history.find(fixture.ownerId(), fixture.weatherId()))
                .containsExactlyInAnyOrder(fixture.topId(), fixture.bottomId());
    }

    @Test
    void 다른_사용자_의상_ID를_반환하면_기존_추천으로_복구한다() throws Exception {
        Fixture fixture = fixture();
        when(client.serializeInput(any())).thenReturn("input");
        when(client.recommend(any(), anyString())).thenReturn(new LlmOutfitDecision(
                List.of(fixture.otherDressId()), List.of(new LlmOutfitDecision.Analysis(fixture.otherDressId(), List.of())), "잘못된 선택"));

        mvc.perform(get("/api/recommendations").header("Authorization", "Bearer " + token(fixture.ownerId()))
                        .param("weatherId", fixture.weatherId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clothes.length()").value(2))
                .andExpect(jsonPath("$.clothes[0].clothesId").value(fixture.topId().toString()));
        assertThat(history.find(fixture.ownerId(), fixture.weatherId()))
                .containsExactlyInAnyOrder(fixture.topId(), fixture.bottomId());
    }

    private Fixture fixture() {
        cleanupFixture = new TransactionTemplate(transactionManager).execute(status -> {
            User owner = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "사용자"));
            User other = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "다른 사용자"));
            profiles.save(new Profile(owner, null, null, null, null, null, 3, List.of()));
            Clothes top = clothes.save(new Clothes("기본 상의", ClothesType.TOP, null, owner));
            Clothes bottom = clothes.save(new Clothes("하이웨이스트 하의", ClothesType.BOTTOM, null, owner));
            Clothes dress = clothes.save(new Clothes("시스루 크롭 원피스", ClothesType.DRESS, null, owner));
            Clothes otherDress = clothes.save(new Clothes("다른 사용자 원피스", ClothesType.DRESS, null, other));
            var definition = definitions.save(new ClothesAttributeDefinition("원단 특징", List.of("비침 있음")));
            attributes.save(new ClothesAttribute(dress, definition, "비침 있음"));
            Instant now = Instant.parse("2026-11-21T00:00:00Z");
            Weather weather = weathers.save(Weather.builder().latitude(37.5).longitude(127.0)
                    .forecastedAt(now).forecastAt(now.plusSeconds(3600)).temperature(24.0)
                    .humidity(70.0).windSpeed(3.0).skyStatus(SkyStatus.CLOUDY)
                    .precipitationType(PrecipitationType.RAIN).precipitationAmount(1.0)
                    .precipitationProbability(80.0).build());
            return new Fixture(owner.getId(), other.getId(), weather.getId(), top.getId(), bottom.getId(),
                    dress.getId(), otherDress.getId(), definition.getId());
        });
        return cleanupFixture;
    }

    private String token(UUID ownerId) {
        String token = tokenProvider.createAccessToken(ownerId);
        when(jwtRegistry.hasActiveJwtInformationByAccessToken(token)).thenReturn(true);
        return token;
    }

    private record Fixture(UUID ownerId, UUID otherOwnerId, UUID weatherId, UUID topId, UUID bottomId,
                           UUID dressId, UUID otherDressId, UUID definitionId) { }
}
