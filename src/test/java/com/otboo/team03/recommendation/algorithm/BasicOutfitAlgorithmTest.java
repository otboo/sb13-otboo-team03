package com.otboo.team03.recommendation.algorithm;

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
}