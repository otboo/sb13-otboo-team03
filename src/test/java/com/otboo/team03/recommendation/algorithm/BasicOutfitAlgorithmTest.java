package com.otboo.team03.recommendation.algorithm;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BasicOutfitAlgorithmTest {

    @Test
    @DisplayName("보유 의상이 없으면 빈 목록을 반환한다")
    void 보유_의상_없을시_빈목록_반환() {
        // given
        BasicOutfitAlgorithm algorithm = new BasicOutfitAlgorithm();

        // when
        var result = algorithm.recommend(List.of());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("상의 하나와 하의 하나가 있으면 두 의상을 반환한다")
    void 상의_하의_각각_하나면_두_의상_반환() {
        // given
        BasicOutfitAlgorithm algorithm = new BasicOutfitAlgorithm();
        User owner = new User("user@example.com", "encoded-password", "사용자");
        Clothes top = new Clothes("상의", ClothesType.TOP, null, owner);
        Clothes bottom = new Clothes("하의", ClothesType.BOTTOM, null, owner);

        // when
        var result = algorithm.recommend(List.of(top, bottom));

        // then
        assertThat(result).containsExactlyInAnyOrder(top, bottom);
    }
}
