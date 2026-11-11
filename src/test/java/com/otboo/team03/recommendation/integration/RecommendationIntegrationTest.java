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
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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

import static org.hamcrest.Matchers.contains;
import static org.assertj.core.api.Assertions.assertThat;
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

    @ParameterizedTest
    @CsvSource({
            "28.0, 두꺼움, false", "28.0, 두꺼움, true",
            "10.0, 얇음, false", "10.0, 얇음, true"
    })
    void DB에_선호와_차선_두께가_없으면_UNKNOWN_상의를_반대_두께보다_우선한다(
            double temperature, String unsuitableValue, boolean hasUnsupportedAttribute) throws Exception {
        User owner = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "정보 부족 사용자"));
        profiles.save(new Profile(owner, null, null, null, null, null, 3, List.of()));
        var definition = definitions.save(new ClothesAttributeDefinition(
                "두께", List.of("얇음", "보통", "두꺼움")));
        saveClothes("반대 두께 상의", ClothesType.TOP, owner, definition, unsuitableValue);
        Clothes unknown = clothes.save(new Clothes("두께 미상 상의", ClothesType.TOP, null, owner));
        if (hasUnsupportedAttribute) {
            var color = definitions.save(new ClothesAttributeDefinition("색상", List.of("검정")));
            attributes.save(new ClothesAttribute(unknown, color, "검정"));
        }
        Clothes bottom = saveClothes("하의", ClothesType.BOTTOM, owner, definition, "보통");
        UUID ownerId = owner.getId();
        UUID weatherId = saveWeather(temperature).getId();
        UUID unknownId = unknown.getId();
        UUID bottomId = bottom.getId();
        String token = flushAndAuthenticate(ownerId);

        mvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", weatherId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clothes.length()").value(2))
                .andExpect(jsonPath("$.clothes[0].clothesId").value(unknownId.toString()))
                .andExpect(jsonPath("$.clothes[0].attributes.length()").value(hasUnsupportedAttribute ? 1 : 0))
                .andExpect(jsonPath("$.clothes[1].clothesId").value(bottomId.toString()));
    }

    @ParameterizedTest
    @CsvSource({"28.0, 두꺼움", "10.0, 얇음"})
    void DB에_부적합한_두께만_있어도_추천_API는_기본_조합을_반환한다(
            double temperature, String value) throws Exception {
        User owner = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "최종 대안 사용자"));
        profiles.save(new Profile(owner, null, null, null, null, null, 3, List.of()));
        var definition = definitions.save(new ClothesAttributeDefinition("두께", List.of("얇음", "두꺼움")));
        Clothes top = saveClothes("상의", ClothesType.TOP, owner, definition, value);
        Clothes bottom = saveClothes("하의", ClothesType.BOTTOM, owner, definition, value);
        UUID ownerId = owner.getId();
        UUID weatherId = saveWeather(temperature).getId();
        UUID topId = top.getId();
        UUID bottomId = bottom.getId();
        String token = flushAndAuthenticate(ownerId);

        mvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", weatherId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clothes.length()").value(2))
                .andExpect(jsonPath("$.clothes[0].clothesId").value(topId.toString()))
                .andExpect(jsonPath("$.clothes[0].attributes[0].value").value(value))
                .andExpect(jsonPath("$.clothes[1].clothesId").value(bottomId.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"없음", "TOP", "BOTTOM", "OUTER"})
    void DB의_보유_의상으로_기본_조합을_만들_수_없으면_빈_추천을_반환한다(String availableType) throws Exception {
        User owner = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "조합 부족 사용자"));
        profiles.save(new Profile(owner, null, null, null, null, null, 3, List.of()));
        if (!availableType.equals("없음")) {
            clothes.save(new Clothes("단일 의상", ClothesType.valueOf(availableType), null, owner));
        }
        UUID ownerId = owner.getId();
        // 추운 날에도 아우터만 단독으로 반환하지 않아야 한다.
        UUID weatherId = saveWeather(10.0).getId();
        String token = flushAndAuthenticate(ownerId);

        mvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", weatherId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weatherId").value(weatherId.toString()))
                .andExpect(jsonPath("$.userId").value(ownerId.toString()))
                .andExpect(jsonPath("$.clothes").isEmpty());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "TOP, 28.0, 3, 반팔, false",
            "TOP, 10.0, 3, 긴팔, true",
            "TOP, 17.0, 1, 긴팔, true",
            "TOP, 19.0, 5, 반팔, false",
            "TOP, 19.9, NULL, 긴팔, false",
            "TOP, 20.0, NULL, 반팔, false",
            "DRESS, 28.0, 3, 반팔, false",
            "DRESS, 10.0, 3, 긴팔, true",
            "DRESS, 21.0, 1, 긴팔, false",
            "DRESS, 19.0, 5, 반팔, false"
    }, nullValues = "NULL")
    void 같은_두께의_상의와_원피스는_저장된_소매와_민감도로_추천한다(
            ClothesType type, double temperature, Integer sensitivity,
            String expectedSleeve, boolean needsOuter) throws Exception {
        User owner = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "소매 추천 사용자"));
        profiles.save(new Profile(owner, null, null, null, null, null, sensitivity, List.of()));
        var thickness = definitions.save(new ClothesAttributeDefinition("두께", List.of("보통")));
        var sleeve = definitions.save(new ClothesAttributeDefinition("소매 길이", List.of("반팔", "긴팔")));
        Clothes shortSleeve = saveClothes("반팔 후보", type, owner, thickness, "보통");
        Clothes longSleeve = saveClothes("긴팔 후보", type, owner, thickness, "보통");
        attributes.save(new ClothesAttribute(shortSleeve, sleeve, "반팔"));
        attributes.save(new ClothesAttribute(longSleeve, sleeve, "긴팔"));
        Clothes bottom = type == ClothesType.TOP
                ? saveClothes("하의", ClothesType.BOTTOM, owner, thickness, "보통") : null;
        Clothes outer = saveClothes("아우터", ClothesType.OUTER, owner, thickness, "보통");
        UUID ownerId = owner.getId();
        UUID weatherId = saveWeather(temperature).getId();
        UUID expectedId = expectedSleeve.equals("반팔") ? shortSleeve.getId() : longSleeve.getId();
        UUID bottomId = bottom == null ? null : bottom.getId();
        UUID outerId = outer.getId();
        int baseSize = type == ClothesType.TOP ? 2 : 1;
        String token = flushAndAuthenticate(ownerId);

        var response = mvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", weatherId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(ownerId.toString()))
                .andExpect(jsonPath("$.weatherId").value(weatherId.toString()))
                .andExpect(jsonPath("$.clothes.length()").value(baseSize + (needsOuter ? 1 : 0)))
                .andExpect(jsonPath("$.clothes[0].clothesId").value(expectedId.toString()))
                .andExpect(jsonPath("$.clothes[0].type").value(type.name()))
                .andExpect(jsonPath("$.clothes[0].attributes[?(@.definitionName == '소매 길이')].value")
                        .value(contains(expectedSleeve)));
        if (bottomId != null) {
            response.andExpect(jsonPath("$.clothes[1].clothesId").value(bottomId.toString()));
        }
        if (needsOuter) {
            response.andExpect(jsonPath("$.clothes[" + baseSize + "].clothesId").value(outerId.toString()));
        }
    }

    @ParameterizedTest
    @EnumSource(value = ClothesType.class, names = {"TOP", "DRESS"})
    void 추천_API는_소매보다_두께_점수가_높은_의상을_우선한다(ClothesType type) throws Exception {
        User owner = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "두께 우선 사용자"));
        profiles.save(new Profile(owner, null, null, null, null, null, 3, List.of()));
        var thickness = definitions.save(new ClothesAttributeDefinition("두께", List.of("얇음", "보통")));
        var sleeve = definitions.save(new ClothesAttributeDefinition("소매", List.of("반팔", "긴팔")));
        Clothes mediumShort = saveClothes("보통 반팔", type, owner, thickness, "보통");
        Clothes lightLong = saveClothes("얇은 긴팔", type, owner, thickness, "얇음");
        attributes.save(new ClothesAttribute(mediumShort, sleeve, "반팔"));
        attributes.save(new ClothesAttribute(lightLong, sleeve, "긴팔"));
        if (type == ClothesType.TOP) {
            saveClothes("하의", ClothesType.BOTTOM, owner, thickness, "보통");
        }
        UUID ownerId = owner.getId();
        UUID weatherId = saveWeather(28.0).getId();
        UUID expectedId = lightLong.getId();
        String token = flushAndAuthenticate(ownerId);

        // 합산하면 보통·반팔이 4점으로 더 높지만, 두께 3점인 얇음·긴팔을 선택해야 한다.
        mvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", weatherId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clothes.length()").value(type == ClothesType.TOP ? 2 : 1))
                .andExpect(jsonPath("$.clothes[0].clothesId").value(expectedId.toString()))
                .andExpect(jsonPath("$.clothes[0].attributes[?(@.definitionName == '두께')].value")
                        .value(contains("얇음")))
                .andExpect(jsonPath("$.clothes[0].attributes[?(@.definitionName == '소매')].value")
                        .value(contains("긴팔")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"없음", "미지원 속성", "충돌"})
    void DB의_소매가_없거나_해석할_수_없으면_반대_소매보다_우선한다(String unknownCase) throws Exception {
        User owner = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "소매 미상 사용자"));
        profiles.save(new Profile(owner, null, null, null, null, null, 3, List.of()));
        var thickness = definitions.save(new ClothesAttributeDefinition("두께", List.of("얇음")));
        var sleeve = definitions.save(new ClothesAttributeDefinition("소매", List.of("반팔", "긴팔")));
        Clothes longSleeve = saveClothes("긴팔 상의", ClothesType.TOP, owner, thickness, "얇음");
        attributes.save(new ClothesAttribute(longSleeve, sleeve, "긴팔"));
        Clothes unknown = saveClothes("소매 미상 상의", ClothesType.TOP, owner, thickness, "얇음");
        if (unknownCase.equals("미지원 속성")) {
            var unsupported = definitions.save(new ClothesAttributeDefinition("기장", List.of("반팔")));
            attributes.save(new ClothesAttribute(unknown, unsupported, "반팔"));
        } else if (unknownCase.equals("충돌")) {
            var alias = definitions.save(new ClothesAttributeDefinition("소매 길이", List.of("반팔", "긴팔")));
            attributes.save(new ClothesAttribute(unknown, sleeve, "반팔"));
            attributes.save(new ClothesAttribute(unknown, alias, "긴팔"));
        }
        Clothes bottom = saveClothes("하의", ClothesType.BOTTOM, owner, thickness, "얇음");
        UUID ownerId = owner.getId();
        UUID weatherId = saveWeather(28.0).getId();
        UUID expectedId = unknown.getId();
        UUID bottomId = bottom.getId();
        String token = flushAndAuthenticate(ownerId);

        mvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", weatherId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clothes.length()").value(2))
                .andExpect(jsonPath("$.clothes[0].clothesId").value(expectedId.toString()))
                .andExpect(jsonPath("$.clothes[0].attributes[?(@.definitionName == '두께')].value")
                        .value(contains("얇음")))
                .andExpect(jsonPath("$.clothes[1].clothesId").value(bottomId.toString()));
    }

    @Test
    void DB의_소매_선택값_수정_후_다음_API_요청에서_선택_결과가_바뀐다() throws Exception {
        User owner = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "소매 수정 사용자"));
        profiles.save(new Profile(owner, null, null, null, null, null, 3, List.of()));
        var thickness = definitions.save(new ClothesAttributeDefinition("두께", List.of("얇음")));
        var sleeve = definitions.save(new ClothesAttributeDefinition("소매", List.of("반팔", "긴팔")));
        Clothes target = saveClothes("수정 대상 상의", ClothesType.TOP, owner, thickness, "얇음");
        attributes.save(new ClothesAttribute(target, sleeve, "반팔"));
        Clothes alternative = saveClothes("소매 미상 상의", ClothesType.TOP, owner, thickness, "얇음");
        Clothes bottom = saveClothes("하의", ClothesType.BOTTOM, owner, thickness, "얇음");
        UUID ownerId = owner.getId();
        UUID weatherId = saveWeather(28.0).getId();
        UUID targetId = target.getId();
        UUID alternativeId = alternative.getId();
        UUID bottomId = bottom.getId();
        UUID thicknessId = thickness.getId();
        UUID sleeveId = sleeve.getId();
        String token = flushAndAuthenticate(ownerId);

        mvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", weatherId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clothes.length()").value(2))
                .andExpect(jsonPath("$.clothes[0].clothesId").value(targetId.toString()))
                .andExpect(jsonPath("$.clothes[0].attributes[?(@.definitionName == '소매')].value")
                        .value(contains("반팔")));

        clothesService.update(targetId, new ClothesUpdateRequest(null, null, List.of(
                new ClothesAttributeDto(thicknessId, "얇음"),
                new ClothesAttributeDto(sleeveId, "긴팔"))), ownerId);
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", weatherId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clothes.length()").value(2))
                .andExpect(jsonPath("$.clothes[0].clothesId").value(alternativeId.toString()))
                .andExpect(jsonPath("$.clothes[0].attributes[?(@.definitionName == '두께')].value")
                        .value(contains("얇음")))
                .andExpect(jsonPath("$.clothes[1].clothesId").value(bottomId.toString()));
    }

    @Test
    void 추천_의상_개수가_늘어도_SQL_조회_횟수는_증가하지_않는다() throws Exception {
        User singleOwner = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "원피스 사용자"));
        User tripleOwner = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "세 벌 사용자"));
        profiles.save(new Profile(singleOwner, null, null, null, null, null, 3, List.of()));
        profiles.save(new Profile(tripleOwner, null, null, null, null, null, 3, List.of()));
        var thickness = definitions.save(new ClothesAttributeDefinition("두께", List.of("보통")));
        Clothes dress = saveClothes("원피스", ClothesType.DRESS, singleOwner, thickness, "보통");
        Clothes top = saveClothes("상의", ClothesType.TOP, tripleOwner, thickness, "보통");
        Clothes bottom = saveClothes("하의", ClothesType.BOTTOM, tripleOwner, thickness, "보통");
        Clothes outer = saveClothes("아우터", ClothesType.OUTER, tripleOwner, thickness, "보통");
        UUID singleOwnerId = singleOwner.getId();
        UUID tripleOwnerId = tripleOwner.getId();
        UUID weatherId = saveWeather(10.0).getId();
        UUID dressId = dress.getId();
        UUID topId = top.getId();
        UUID bottomId = bottom.getId();
        UUID outerId = outer.getId();
        String singleToken = flushAndAuthenticate(singleOwnerId);
        String tripleToken = tokenProvider.createAccessToken(tripleOwnerId);
        when(jwtRegistry.hasActiveJwtInformationByAccessToken(tripleToken)).thenReturn(true);
        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        boolean previousEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            mvc.perform(get("/api/recommendations")
                            .header("Authorization", "Bearer " + singleToken)
                            .param("weatherId", weatherId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(singleOwnerId.toString()))
                    .andExpect(jsonPath("$.clothes[*].clothesId").value(contains(dressId.toString())))
                    .andExpect(jsonPath("$.clothes[0].attributes[?(@.definitionName == '두께')].value")
                            .value(contains("보통")));
            long singleQueries = statistics.getPrepareStatementCount();

            // 두 번째 요청도 캐시된 의상·정의·날씨 없이 같은 조건에서 측정한다.
            entityManager.clear();
            statistics.clear();
            mvc.perform(get("/api/recommendations")
                            .header("Authorization", "Bearer " + tripleToken)
                            .param("weatherId", weatherId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(tripleOwnerId.toString()))
                    .andExpect(jsonPath("$.clothes[*].clothesId")
                            .value(contains(topId.toString(), bottomId.toString(), outerId.toString())))
                    .andExpect(jsonPath("$.clothes[*].attributes[?(@.definitionName == '두께')].value")
                            .value(contains("보통", "보통", "보통")));
            long tripleQueries = statistics.getPrepareStatementCount();

            assertThat(tripleQueries)
                    .as("추천 1벌의 SQL 횟수=%d, 추천 3벌의 SQL 횟수=%d", singleQueries, tripleQueries)
                    .isEqualTo(singleQueries);
        } finally {
            statistics.setStatisticsEnabled(previousEnabled);
        }
    }

    @Test
    void 의상_속성과_정의를_SQL_한번으로_조회하고_정의명_접근시_추가_조회하지_않는다() {
        User owner = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "속성 조회 사용자"));
        var thickness = definitions.save(new ClothesAttributeDefinition("두께", List.of("얇음")));
        var sleeve = definitions.save(new ClothesAttributeDefinition("소매", List.of("반팔")));
        Clothes top = saveClothes("상의", ClothesType.TOP, owner, thickness, "얇음");
        attributes.save(new ClothesAttribute(top, sleeve, "반팔"));
        Clothes bottom = saveClothes("하의", ClothesType.BOTTOM, owner, thickness, "얇음");
        List<UUID> clothesIds = List.of(top.getId(), bottom.getId());
        // 저장 SQL과 영속성 컨텍스트에 남은 정의가 조회 횟수 검증에 영향을 주지 않도록 한다.
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        boolean previousEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            List<ClothesAttribute> result = attributes.findAllByClothes_IdIn(clothesIds);

            assertThat(result).hasSize(3);
            assertThat(statistics.getPrepareStatementCount())
                    .as("속성과 정의를 함께 조회하는 SQL 횟수")
                    .isEqualTo(1);
            assertThat(result).extracting(attribute -> attribute.getDefinition().getName())
                    .containsExactlyInAnyOrder("두께", "소매", "두께");
            assertThat(statistics.getPrepareStatementCount())
                    .as("정의 이름에 접근한 후의 전체 SQL 횟수")
                    .isEqualTo(1);
        } finally {
            statistics.setStatisticsEnabled(previousEnabled);
        }
    }

    private String flushAndAuthenticate(UUID ownerId) {
        entityManager.flush();
        entityManager.clear();
        String token = tokenProvider.createAccessToken(ownerId);
        when(jwtRegistry.hasActiveJwtInformationByAccessToken(token)).thenReturn(true);
        return token;
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
