package com.otboo.team03.recommendation.algorithm;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static com.otboo.team03.recommendation.algorithm.ThicknessLevel.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BasicOutfitAlgorithmTest {

    private BasicOutfitAlgorithm algorithm;
    private User owner;

    @BeforeEach
    void setUp() {
        algorithm = new BasicOutfitAlgorithm(new ThicknessSuitabilityScorer());
        owner = new User("user@example.com", "encoded-password", "사용자");
    }

    @Nested
    @DisplayName("기본 의상 추천 알고리즘")
    class BasicOutfitRecommendation {

        @Test
        @DisplayName("보유 의상이 없으면 빈 목록을 반환한다")
        void 보유_의상_없을시_빈목록_반환() {
            // when
            var result = algorithm.recommend(List.of());

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("상의 하나와 하의 하나가 있으면 두 의상을 반환한다")
        void 상의_하의_각각_하나면_두_의상_반환() {
            // given
            Clothes top = new Clothes("상의", ClothesType.TOP, null, owner);
            Clothes bottom = new Clothes("하의", ClothesType.BOTTOM, null, owner);

            // when
            var result = algorithm.recommend(List.of(top, bottom));

            // then
            assertThat(result).containsExactlyInAnyOrder(top, bottom);
        }

        @Test
        @DisplayName("원피스 하나만 있으면 원피스를 반환한다")
        void 원피스_하나면_원피스_반환() {
            // given
            Clothes dress = new Clothes("원피스", ClothesType.DRESS, null, owner);

            // when
            var result = algorithm.recommend(List.of(dress));

            // then
            assertThat(result).containsExactly(dress);
        }

        @Test
        @DisplayName("원피스가 여러 개면 입력 순서상 첫 번째 원피스만 반환한다")
        void 원피스가_여러개면_첫번째_원피스만_반환() {
            // given
            Clothes firstDress = new Clothes("첫 원피스", ClothesType.DRESS, null, owner);
            Clothes secondDress = new Clothes("두 번째 원피스", ClothesType.DRESS, null, owner);

            // when
            var result = algorithm.recommend(List.of(firstDress, secondDress));

            // then
            assertThat(result).containsExactly(firstDress);
        }

        @Test
        @DisplayName("상의만 있으면 빈 목록을 반환한다")
        void 상의만_있으면_빈목록_반환() {
            // given
            Clothes top = new Clothes("상의", ClothesType.TOP, null, owner);

            // when
            var result = algorithm.recommend(List.of(top));

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("하의만 있으면 빈 목록을 반환한다")
        void 하의만_있으면_빈목록_반환() {
            // given
            Clothes bottom = new Clothes("하의", ClothesType.BOTTOM, null, owner);

            // when
            var result = algorithm.recommend(List.of(bottom));

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("상의와 원피스가 있으면 원피스만 반환한다")
        void 상의와_원피스가_있으면_원피스만_반환() {
            // given
            Clothes top = new Clothes("상의", ClothesType.TOP, null, owner);
            Clothes dress = new Clothes("원피스", ClothesType.DRESS, null, owner);

            // when
            var result = algorithm.recommend(List.of(top, dress));

            // then
            assertThat(result).containsExactly(dress);
        }

        @Test
        @DisplayName("하의와 원피스가 있으면 원피스만 반환한다")
        void 하의와_원피스가_있으면_원피스만_반환() {
            // given
            Clothes bottom = new Clothes("하의", ClothesType.BOTTOM, null, owner);
            Clothes dress = new Clothes("원피스", ClothesType.DRESS, null, owner);

            // when
            var result = algorithm.recommend(List.of(bottom, dress));

            // then
            assertThat(result).containsExactly(dress);
        }
        @Test
        @DisplayName("상의·하의·원피스가 모두 있으면 상의와 하의만 반환한다")
        void 모든_기본_종류가_있으면_상의와_하의_우선() {
            // given
            Clothes top = new Clothes("상의", ClothesType.TOP, null, owner);
            Clothes bottom = new Clothes("하의", ClothesType.BOTTOM, null, owner);
            Clothes dress = new Clothes("원피스", ClothesType.DRESS, null, owner);

            // when
            var result = algorithm.recommend(List.of(dress, top, bottom));

            // then
            assertThat(result).containsExactlyInAnyOrder(top, bottom);
        }

        @Test
        @DisplayName("상의가 여러 개면 입력 순서상 첫 번째 상의를 선택한다")
        void 상의가_여러개면_첫번째_상의_선택() {
            // given
            Clothes firstTop = new Clothes("첫 상의", ClothesType.TOP, null, owner);
            Clothes secondTop = new Clothes("두 번째 상의", ClothesType.TOP, null, owner);
            Clothes bottom = new Clothes("하의", ClothesType.BOTTOM, null, owner);

            // when
            var result = algorithm.recommend(List.of(bottom, firstTop, secondTop));

            // then
            assertThat(result).containsExactlyInAnyOrder(firstTop, bottom);
        }

        @Test
        @DisplayName("하의가 여러 개면 입력 순서상 첫 번째 하의를 선택한다")
        void 하의가_여러개면_첫번째_하의_선택() {
            // given
            Clothes top = new Clothes("상의", ClothesType.TOP, null, owner);
            Clothes firstBottom = new Clothes("첫 하의", ClothesType.BOTTOM, null, owner);
            Clothes secondBottom = new Clothes("두 번째 하의", ClothesType.BOTTOM, null, owner);

            // when
            var result = algorithm.recommend(List.of(firstBottom, top, secondBottom));

            // then
            assertThat(result).containsExactlyInAnyOrder(top, firstBottom);
        }

        @Test
        @DisplayName("아우터·신발·액세서리만 있으면 빈 목록을 반환한다")
        void 아우터_신발_액세서리만_있으면_빈목록_반환() {
            // given
            Clothes outer = new Clothes("아우터", ClothesType.OUTER, null, owner);
            Clothes shoes = new Clothes("신발", ClothesType.SHOES, null, owner);
            Clothes accessory = new Clothes("액세서리", ClothesType.ACCESSORY, null, owner);

            // when
            var result = algorithm.recommend(List.of(outer, shoes, accessory));

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("상의·하의와 함께 아우터가 있어도 상의와 하의만 반환한다")
        void 상의_하의_아우터가_있으면_상의와_하의만_반환() {
            // given
            Clothes top = new Clothes("상의", ClothesType.TOP, null, owner);
            Clothes bottom = new Clothes("하의", ClothesType.BOTTOM, null, owner);
            Clothes outer = new Clothes("아우터", ClothesType.OUTER, null, owner);

            // when
            var result = algorithm.recommend(List.of(outer, top, bottom));

            // then
            assertThat(result).containsExactlyInAnyOrder(top, bottom);
        }
    }

    @Nested
    @DisplayName("날씨·온도 민감도에 따른 의상 추천")
    class WeatherAndTemperatureSensitivityRecommendation {

        private Clothes top;
        private Clothes bottom;
        private Clothes outer;
        private List<Clothes> clothes;

        @BeforeEach
        void setUpClothes() {
            top = new Clothes("상의", ClothesType.TOP, null, owner);
            bottom = new Clothes("하의", ClothesType.BOTTOM, null, owner);
            outer = new Clothes("아우터", ClothesType.OUTER, null, owner);
            clothes = List.of(top, bottom, outer);
        }

        @Test
        @DisplayName("기온 17℃에서 민감도 1이면 상의·하의와 아우터를 반환한다")
        void 민감도_1이면_아우터를_추가한다() {
            // when
            var result = algorithm.recommend(clothes, 17.0, 1);

            // then
            assertThat(result).containsExactlyInAnyOrder(top, bottom, outer);
        }

        @Test
        @DisplayName("기온 17℃에서 민감도 3이면 상의와 하의만 반환한다")
        void 민감도_3이면_기온_기준으로_아우터를_제외한다() {
            // when
            var result = algorithm.recommend(clothes, 17.0, 3);

            // then
            assertThat(result).containsExactlyInAnyOrder(top, bottom);
        }

        @Test
        @DisplayName("기온 13℃에서 민감도 5이면 상의와 하의만 반환한다")
        void 민감도_5이면_보정하여_아우터를_제외한다() {
            // when
            var result = algorithm.recommend(clothes, 13.0, 5);

            // then
            assertThat(result).containsExactlyInAnyOrder(top, bottom);
        }

        @Test
        @DisplayName("기온 13℃에서 민감도 3이면 상의·하의와 아우터를 반환한다")
        void 기온_13도_민감도_3이면_아우터를_추가한다() {
            // when
            var result = algorithm.recommend(clothes, 13.0, 3);

            // then
            assertThat(result).containsExactlyInAnyOrder(top, bottom, outer);
        }

        @Test
        @DisplayName("기온 -5℃에서 민감도 3이면 상의·하의와 아우터를 반환한다")
        void 영하_5도_민감도_3이면_아우터를_추가한다() {
            // when
            var result = algorithm.recommend(clothes, -5.0, 3);

            // then
            assertThat(result).containsExactlyInAnyOrder(top, bottom, outer);
        }

        @Test
        @DisplayName("기온 10℃에서 민감도 3이어도 아우터가 없으면 기본 코디만 반환한다")
        void 추운_날_아우터가_없으면_기본_코디만_반환한다() {
            // when
            var result = algorithm.recommend(List.of(top, bottom), 10.0, 3);

            // then
            assertThat(result).containsExactlyInAnyOrder(top, bottom);
        }

        @Test
        @DisplayName("추운 날 아우터가 여러 개면 입력 순서상 첫 번째 아우터를 선택한다")
        void 아우터가_여러개면_첫번째_아우터를_선택한다() {
            // given
            Clothes secondOuter = new Clothes("두 번째 아우터", ClothesType.OUTER, null, owner);

            // when
            var result = algorithm.recommend(List.of(top, outer, bottom, secondOuter), 10.0, 3);

            // then
            assertThat(result).containsExactlyInAnyOrder(top, bottom, outer);
        }

        @Test
        @DisplayName("추운 날 아우터만 있고 기본 코디를 구성할 수 없으면 빈 목록을 반환한다")
        void 추운_날_아우터만_있으면_빈목록을_반환한다() {
            // when
            var result = algorithm.recommend(List.of(outer), 10.0, 3);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("추운 날에도 보유 의상이 없으면 빈 목록을 반환한다")
        void 추운_날_보유_의상이_없으면_빈목록을_반환한다() {
            // when
            var result = algorithm.recommend(List.of(), 10.0, 3);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("기온 14.9℃에서 민감도 3이면 아우터를 포함한다")
        void 기준_기온_미만이면_아우터를_포함한다() {
            // when
            var result = algorithm.recommend(clothes, 14.9, 3);

            // then
            assertThat(result).containsExactlyInAnyOrder(top, bottom, outer);
        }

        @Test
        @DisplayName("기온이 정확히 15℃이고 민감도 3이면 아우터를 포함하지 않는다")
        void 기준_기온과_같으면_아우터를_제외한다() {
            // when
            var result = algorithm.recommend(clothes, 15.0, 3);

            // then
            assertThat(result).containsExactlyInAnyOrder(top, bottom);
        }

        @Test
        @DisplayName("원피스 코디도 기온 10℃에서 민감도 3이면 아우터를 추가한다")
        void 추운_날_원피스_코디에_아우터를_추가한다() {
            // given
            Clothes dress = new Clothes("원피스", ClothesType.DRESS, null, owner);

            // when
            var result = algorithm.recommend(List.of(dress, outer), 10.0, 3);

            // then
            assertThat(result).containsExactlyInAnyOrder(dress, outer);
        }
    }

    @Nested
    @DisplayName("속성 기반 의상 추천")
    class AttributeBasedRecommendation {

        @ParameterizedTest
        @CsvSource({"TOP", "BOTTOM", "DRESS", "OUTER"})
        void 같은_종류의_차선_후보가_동점이면_입력_순서를_유지한다(ClothesType type) {
            Clothes lowerPriority = clothes(type);
            Clothes firstAlternative = clothes(type);
            Clothes secondAlternative = clothes(type);
            List<Clothes> base = switch (type) {
                case TOP -> List.of(clothes(ClothesType.BOTTOM));
                case BOTTOM -> List.of(clothes(ClothesType.TOP));
                case DRESS -> List.of();
                case OUTER -> List.of(clothes(ClothesType.TOP), clothes(ClothesType.BOTTOM));
                default -> throw new IllegalArgumentException();
            };
            Map<UUID, ThicknessLevel> levels = Map.of(
                    lowerPriority.getId(), LIGHT,
                    firstAlternative.getId(), MEDIUM,
                    secondAlternative.getId(), MEDIUM);
            List<Clothes> candidates = new ArrayList<>(
                    List.of(lowerPriority, firstAlternative, secondAlternative));
            candidates.addAll(base);
            List<Clothes> reversedCandidates = new ArrayList<>(
                    List.of(lowerPriority, secondAlternative, firstAlternative));
            reversedCandidates.addAll(base);

            var firstResult = algorithm.recommend(candidates, levels, 10.0, 3);
            var reversedResult = algorithm.recommend(reversedCandidates, levels, 10.0, 3);

            List<Clothes> firstExpected = new ArrayList<>(base);
            firstExpected.add(firstAlternative);
            List<Clothes> reversedExpected = new ArrayList<>(base);
            reversedExpected.add(secondAlternative);
            assertThat(firstResult).containsExactlyInAnyOrderElementsOf(firstExpected);
            assertThat(reversedResult).containsExactlyInAnyOrderElementsOf(reversedExpected);
        }

        @ParameterizedTest
        @CsvSource({
                "28.0, 3, HEAVY, MEDIUM",
                "10.0, 3, LIGHT, MEDIUM",
                "21.0, 1, LIGHT, HEAVY",
                "22.0, 2, LIGHT, HEAVY",
                "18.0, 4, HEAVY, LIGHT",
                "19.0, 5, HEAVY, LIGHT",
                "19.9, 3, LIGHT, HEAVY",
                "20.0, 3, HEAVY, LIGHT",
                "28.0, 3, HEAVY, UNKNOWN",
                "10.0, 3, LIGHT, UNKNOWN",
                "21.0, 1, LIGHT, UNKNOWN",
                "19.0, 5, HEAVY, UNKNOWN"
        })
        void 선호_두께가_없으면_온도와_민감도에_맞는_차선_후보를_선택한다(
                double temperature, int sensitivity,
                ThicknessLevel lowerPriority, ThicknessLevel higherPriority) {
            Clothes first = clothes(ClothesType.TOP);
            Clothes alternative = clothes(ClothesType.TOP);
            Clothes bottom = clothes(ClothesType.BOTTOM);

            var result = algorithm.recommend(List.of(first, alternative, bottom),
                    Map.of(first.getId(), lowerPriority, alternative.getId(), higherPriority),
                    temperature, sensitivity);

            assertThat(result).containsExactlyInAnyOrder(alternative, bottom);
        }

        @ParameterizedTest
        @CsvSource({"28.0, HEAVY", "10.0, LIGHT"})
        void 부적합한_두께만_있어도_기본_조합을_반환한다(
                double temperature, ThicknessLevel level) {
            Clothes top = clothes(ClothesType.TOP);
            Clothes bottom = clothes(ClothesType.BOTTOM);

            var result = algorithm.recommend(List.of(top, bottom),
                    Map.of(top.getId(), level, bottom.getId(), level), temperature, 3);

            assertThat(result).containsExactlyInAnyOrder(top, bottom);
        }

        @Test
        void 적합도가_같으면_입력_순서를_유지한다() {
            Clothes heavy = clothes(ClothesType.TOP);
            Clothes firstLight = clothes(ClothesType.TOP);
            Clothes secondLight = clothes(ClothesType.TOP);
            Clothes bottom = clothes(ClothesType.BOTTOM);
            Map<UUID, ThicknessLevel> levels = Map.of(
                    heavy.getId(), HEAVY, firstLight.getId(), LIGHT, secondLight.getId(), LIGHT);

            var firstResult = algorithm.recommend(
                    List.of(heavy, firstLight, secondLight, bottom), levels, 28.0, 3);
            var reversedResult = algorithm.recommend(
                    List.of(heavy, secondLight, firstLight, bottom), levels, 28.0, 3);

            assertThat(firstResult).containsExactlyInAnyOrder(firstLight, bottom);
            assertThat(reversedResult).containsExactlyInAnyOrder(secondLight, bottom);
        }

        @Test
        void 보정_온도를_두께_선택과_아우터_추가에_함께_적용한다() {
            Clothes light = clothes(ClothesType.TOP);
            Clothes heavy = clothes(ClothesType.TOP);
            Clothes bottom = clothes(ClothesType.BOTTOM);
            Clothes outer = clothes(ClothesType.OUTER);

            var result = algorithm.recommend(List.of(light, heavy, bottom, outer),
                    Map.of(light.getId(), LIGHT, heavy.getId(), HEAVY, outer.getId(), HEAVY),
                    17.0, 1);

            // 보정 온도 13도에서 두꺼운 상의를 선택하고 아우터도 추가해야 한다.
            assertThat(result).containsExactlyInAnyOrder(heavy, bottom, outer);
        }

        @ParameterizedTest
        @CsvSource({"LIGHT, false", "MEDIUM, false", "HEAVY, true"})
        void ID가_없는_의상은_UNKNOWN으로_평가한다(
                ThicknessLevel knownLevel, boolean expectWithoutId) {
            Clothes withoutId = new Clothes("ID 없는 상의", ClothesType.TOP, null, owner);
            Clothes known = clothes(ClothesType.TOP);
            Clothes bottom = clothes(ClothesType.BOTTOM);
            assertThat(withoutId.getId()).isNull();
            // 잘못 같은 수준으로 평가해도 입력 순서만으로 통과하지 않도록 배치한다.
            List<Clothes> candidates = expectWithoutId
                    ? List.of(known, withoutId, bottom)
                    : List.of(withoutId, known, bottom);

            var result = algorithm.recommend(candidates,
                    Map.of(known.getId(), knownLevel), 28.0, 3);

            Clothes expectedTop = expectWithoutId ? withoutId : known;
            assertThat(result).containsExactlyInAnyOrder(expectedTop, bottom);
        }

        @Test
        void ID가_없는_상의와_하의도_후보에_유지하여_기본_조합을_반환한다() {
            Clothes top = new Clothes("ID 없는 상의", ClothesType.TOP, null, owner);
            Clothes bottom = new Clothes("ID 없는 하의", ClothesType.BOTTOM, null, owner);
            assertThat(top.getId()).isNull();
            assertThat(bottom.getId()).isNull();

            var result = algorithm.recommend(List.of(top, bottom), Map.of(), 28.0, 3);

            assertThat(result).containsExactlyInAnyOrder(top, bottom);
        }

        @ParameterizedTest
        @CsvSource({"28.0, 3, LIGHT", "10.0, 3, HEAVY", "17.0, 1, HEAVY", "20.0, 5, MEDIUM"})
        void 입력_순서보다_보정_온도에_적합한_상의를_우선한다(
                double temperature, int sensitivity, ThicknessLevel preferred) {
            Clothes light = clothes(ClothesType.TOP);
            Clothes medium = clothes(ClothesType.TOP);
            Clothes heavy = clothes(ClothesType.TOP);
            Clothes bottom = clothes(ClothesType.BOTTOM);
            Clothes expected = switch (preferred) {
                case LIGHT -> light;
                case MEDIUM -> medium;
                case HEAVY -> heavy;
                default -> throw new IllegalArgumentException();
            };
            // 적합한 후보가 맨 앞에 있을 때만 통과하는 구현을 방지한다.
            List<Clothes> candidates = preferred == HEAVY
                    ? List.of(light, medium, heavy, bottom)
                    : List.of(heavy, light, medium, bottom);

            var result = algorithm.recommend(candidates,
                    Map.of(light.getId(), LIGHT, medium.getId(), MEDIUM, heavy.getId(), HEAVY),
                    temperature, sensitivity);

            assertThat(result).containsExactlyInAnyOrder(expected, bottom);
        }

        @Test
        void 더운_날에는_적합한_하의를_선택한다() {
            Clothes top = clothes(ClothesType.TOP);
            Clothes heavy = clothes(ClothesType.BOTTOM);
            Clothes light = clothes(ClothesType.BOTTOM);

            var result = algorithm.recommend(List.of(top, heavy, light),
                    Map.of(heavy.getId(), HEAVY, light.getId(), LIGHT), 28.0, 3);

            assertThat(result).containsExactlyInAnyOrder(top, light);
        }

        @Test
        void 기본_조합이_없으면_적합한_원피스를_선택한다() {
            Clothes heavy = clothes(ClothesType.DRESS);
            Clothes light = clothes(ClothesType.DRESS);

            var result = algorithm.recommend(List.of(heavy, light),
                    Map.of(heavy.getId(), HEAVY, light.getId(), LIGHT), 28.0, 3);

            assertThat(result).containsExactly(light);
        }

        @Test
        void 추운_날에는_기본_조합에_적합한_아우터를_추가한다() {
            Clothes top = clothes(ClothesType.TOP);
            Clothes bottom = clothes(ClothesType.BOTTOM);
            Clothes light = clothes(ClothesType.OUTER);
            Clothes heavy = clothes(ClothesType.OUTER);

            var result = algorithm.recommend(List.of(top, bottom, light, heavy),
                    Map.of(light.getId(), LIGHT, heavy.getId(), HEAVY), 10.0, 3);

            assertThat(result).containsExactlyInAnyOrder(top, bottom, heavy);
        }

        @Test
        void 두께_정보가_없어도_기본_조합을_구성한다() {
            Clothes top = clothes(ClothesType.TOP);
            Clothes bottom = clothes(ClothesType.BOTTOM);

            var result = algorithm.recommend(List.of(top, bottom), Map.of(), 20.0, 3);

            assertThat(result).containsExactlyInAnyOrder(top, bottom);
        }

        @Test
        void 두께_정보가_있어도_기본_조합_없이_아우터만_추천하지_않는다() {
            Clothes outer = clothes(ClothesType.OUTER);

            var result = algorithm.recommend(List.of(outer),
                    Map.of(outer.getId(), HEAVY), 10.0, 3);

            assertThat(result).isEmpty();
        }

        private Clothes clothes(ClothesType type) {
            Clothes clothes = mock(Clothes.class);
            when(clothes.getType()).thenReturn(type);
            when(clothes.getId()).thenReturn(UUID.randomUUID());
            return clothes;
        }
    }
}
