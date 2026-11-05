package com.otboo.team03.recommendation.algorithm;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BasicOutfitAlgorithmTest {

    private BasicOutfitAlgorithm algorithm;
    private User owner;

    @BeforeEach
    void setUp() {
        algorithm = new BasicOutfitAlgorithm();
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
}
