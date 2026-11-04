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

    }
}
