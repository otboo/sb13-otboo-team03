package com.otboo.team03.recommendation.integration;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesAttribute;
import com.otboo.team03.clothes.entity.ClothesAttributeDefinition;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.clothes.dto.request.ClothesAttributeDto;
import com.otboo.team03.clothes.dto.request.ClothesAttributeDefUpdateRequest;
import com.otboo.team03.clothes.dto.request.ClothesUpdateRequest;
import com.otboo.team03.clothes.service.ClothesService;
import com.otboo.team03.clothes.service.ClothesAttributeDefinitionService;
import com.otboo.team03.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.team03.clothes.repository.ClothesAttributeRepository;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.global.security.jwt.JwtTokenProvider;
import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.profile.repository.ProfileRepository;
import com.otboo.team03.support.PostgresTestSupport;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.repository.WeatherRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 PostgreSQL·서비스·알고리즘·JWT 필터를 검증하며 Redis의 토큰 등록 여부만 대체한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecommendationIntegrationTest extends PostgresTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private ProfileRepository profiles;
    @Autowired private ClothesRepository clothes;
    @Autowired private ClothesAttributeDefinitionRepository definitions;
    @Autowired private ClothesAttributeRepository attributes;
    @Autowired private WeatherRepository weathers;
    @Autowired private EntityManager entityManager;
    @Autowired private JwtTokenProvider tokenProvider;
    @Autowired private ClothesService clothesService;
    @Autowired private ClothesAttributeDefinitionService definitionService;
    @MockitoBean private JwtRegistry jwtRegistry;

    @ParameterizedTest
    @CsvSource({"28.0, 두꺼움, 얇음", "10.0, 얇음, 두꺼움"})
    void 저장된_속성으로_보통_두께를_차선_추천하고_다른_사용자의_의상을_제외한다(
            double temperature, String unsuitableValue, String otherOwnerPreferredValue) throws Exception {
        User owner = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "추천 사용자"));
        User other = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "다른 사용자"));
        profiles.save(new Profile(owner, null, null, null, null, null, 3, List.of()));
        var definition = definitions.save(new ClothesAttributeDefinition(
                "두께", List.of("얇음", "보통", "두꺼움")));
        saveClothes("부적합 상의", ClothesType.TOP, owner, definition, unsuitableValue);
        Clothes alternative = saveClothes("보통 상의", ClothesType.TOP, owner, definition, "보통");
        Clothes bottom = saveClothes("하의", ClothesType.BOTTOM, owner, definition, "보통");
        saveClothes("다른 사용자 선호 상의", ClothesType.TOP, other, definition, otherOwnerPreferredValue);
        Weather weather = saveWeather(temperature);
        UUID ownerId = owner.getId();
        UUID otherId = other.getId();
        UUID weatherId = weather.getId();
        UUID topId = alternative.getId();
        UUID bottomId = bottom.getId();
        UUID definitionId = definition.getId();
        // 영속성 컨텍스트의 객체 대신 실제 DB에서 다시 읽는 흐름을 검증한다.
        entityManager.flush();
        entityManager.clear();
        String token = tokenProvider.createAccessToken(ownerId);
        when(jwtRegistry.hasActiveJwtInformationByAccessToken(token)).thenReturn(true);

        mvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", weatherId.toString())
                        .param("userId", otherId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weatherId").value(weatherId.toString()))
                .andExpect(jsonPath("$.userId").value(ownerId.toString()))
                .andExpect(jsonPath("$.clothes.length()").value(2))
                .andExpect(jsonPath("$.clothes[0].clothesId").value(topId.toString()))
                .andExpect(jsonPath("$.clothes[0].type").value("TOP"))
                .andExpect(jsonPath("$.clothes[0].attributes[0].definitionId").value(definitionId.toString()))
                .andExpect(jsonPath("$.clothes[0].attributes[0].definitionName").value("두께"))
                .andExpect(jsonPath("$.clothes[0].attributes[0].value").value("보통"))
                .andExpect(jsonPath("$.clothes[1].clothesId").value(bottomId.toString()));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "21.0, 1, 두꺼움",
            "22.0, 2, 두꺼움",
            "18.0, 4, 얇음",
            "19.0, 5, 얇음",
            "19.9, 3, 두꺼움",
            "20.0, 3, 얇음",
            "19.9, NULL, 두꺼움",
            "20.0, NULL, 얇음"
    }, nullValues = "NULL")
    void 저장된_민감도와_중립_20도_경계에_따라_차선_상의를_추천한다(
            double temperature, Integer sensitivity, String expectedValue) throws Exception {
        User owner = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "민감도 사용자"));
        profiles.save(new Profile(owner, null, null, null, null, null, sensitivity, List.of()));
        var definition = definitions.save(new ClothesAttributeDefinition(
                "두께", List.of("얇음", "보통", "두꺼움")));
        // 상의에는 보통 두께를 등록하지 않아 민감도에 따른 차선 선택을 검증한다.
        Clothes light = saveClothes("얇은 상의", ClothesType.TOP, owner, definition, "얇음");
        Clothes heavy = saveClothes("두꺼운 상의", ClothesType.TOP, owner, definition, "두꺼움");
        Clothes bottom = saveClothes("하의", ClothesType.BOTTOM, owner, definition, "보통");
        Weather weather = saveWeather(temperature);
        UUID ownerId = owner.getId();
        UUID weatherId = weather.getId();
        UUID expectedTopId = expectedValue.equals("얇음") ? light.getId() : heavy.getId();
        UUID bottomId = bottom.getId();
        entityManager.flush();
        entityManager.clear();
        String token = tokenProvider.createAccessToken(ownerId);
        when(jwtRegistry.hasActiveJwtInformationByAccessToken(token)).thenReturn(true);

        mvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", weatherId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(ownerId.toString()))
                .andExpect(jsonPath("$.weatherId").value(weatherId.toString()))
                .andExpect(jsonPath("$.clothes.length()").value(2))
                .andExpect(jsonPath("$.clothes[0].clothesId").value(expectedTopId.toString()))
                .andExpect(jsonPath("$.clothes[0].attributes[0].value").value(expectedValue))
                .andExpect(jsonPath("$.clothes[1].clothesId").value(bottomId.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"선택값 변경", "속성명 변경"})
    void DB_속성_수정_후_다음_API_요청에서_최신_정보로_재추천한다(String change) throws Exception {
        User owner = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "속성 수정 사용자"));
        profiles.save(new Profile(owner, null, null, null, null, null, 3, List.of()));
        var targetDefinition = definitions.save(new ClothesAttributeDefinition(
                "두께", List.of("얇음", "보통", "두꺼움")));
        // 수정 대상 정의와 분리하여 차선 상의의 보통 두께는 그대로 유지한다.
        var alternativeDefinition = definitions.save(new ClothesAttributeDefinition(
                "두께감", List.of("얇음", "보통", "두꺼움")));
        Clothes target = saveClothes("수정 대상 상의", ClothesType.TOP, owner, targetDefinition, "얇음");
        Clothes alternative = saveClothes("차선 상의", ClothesType.TOP, owner, alternativeDefinition, "보통");
        Clothes bottom = saveClothes("하의", ClothesType.BOTTOM, owner, alternativeDefinition, "보통");
        UUID ownerId = owner.getId();
        UUID weatherId = saveWeather(28.0).getId();
        UUID targetId = target.getId();
        UUID alternativeId = alternative.getId();
        UUID bottomId = bottom.getId();
        UUID targetDefinitionId = targetDefinition.getId();
        entityManager.flush();
        entityManager.clear();
        String token = tokenProvider.createAccessToken(ownerId);
        when(jwtRegistry.hasActiveJwtInformationByAccessToken(token)).thenReturn(true);

        mvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", weatherId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clothes.length()").value(2))
                .andExpect(jsonPath("$.clothes[0].clothesId").value(targetId.toString()))
                .andExpect(jsonPath("$.clothes[0].attributes[0].value").value("얇음"));

        if (change.equals("선택값 변경")) {
            clothesService.update(targetId, new ClothesUpdateRequest(null, null,
                    List.of(new ClothesAttributeDto(targetDefinitionId, "두꺼움"))), ownerId);
        } else {
            definitionService.update(targetDefinitionId,
                    new ClothesAttributeDefUpdateRequest("미지원 속성", null));
        }
        // 변경 SQL을 실행하고 다시 조회하여 기존 관리 객체의 값만 보는 테스트를 방지한다.
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", weatherId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(ownerId.toString()))
                .andExpect(jsonPath("$.clothes.length()").value(2))
                .andExpect(jsonPath("$.clothes[0].clothesId").value(alternativeId.toString()))
                .andExpect(jsonPath("$.clothes[0].attributes[0].definitionName").value("두께감"))
                .andExpect(jsonPath("$.clothes[0].attributes[0].value").value("보통"))
                .andExpect(jsonPath("$.clothes[1].clothesId").value(bottomId.toString()));
    }

    private Weather saveWeather(double temperature) {
        Instant now = Instant.parse("2026-11-09T00:00:00Z");
        return weathers.save(Weather.builder()
                .latitude(37.5).longitude(127.0)
                .forecastedAt(now).forecastAt(now.plusSeconds(3600))
                .skyStatus(SkyStatus.CLEAR).precipitationType(PrecipitationType.NONE)
                .precipitationAmount(0.0).precipitationProbability(0.0)
                .temperature(temperature).humidity(50.0).windSpeed(1.0)
                .build());
    }

    private Clothes saveClothes(String name, ClothesType type, User owner,
                               ClothesAttributeDefinition definition, String value) {
        Clothes item = clothes.save(new Clothes(name, type, null, owner));
        attributes.save(new ClothesAttribute(item, definition, value));
        return item;
    }
}
