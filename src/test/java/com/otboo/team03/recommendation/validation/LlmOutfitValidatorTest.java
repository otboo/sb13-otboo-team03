package com.otboo.team03.recommendation.validation;

import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.recommendation.client.dto.LlmOutfitDecision;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static com.otboo.team03.recommendation.support.LlmTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class LlmOutfitValidatorTest {
    private final LlmOutfitValidator validator = new LlmOutfitValidator();

    @Test
    void 소유_상의하의와_원피스_조합을_허용한다() {
        assertThat(validator.isValid(decision(), input())).isTrue();
        assertThat(validIds(List.of(DRESS_ID))).isTrue();
    }

    @Test
    void 다른_사용자_의상이나_중복_ID는_거부한다() {
        assertThat(validIds(List.of(TOP_ID, UUID.randomUUID()))).isFalse();
        assertThat(validIds(List.of(TOP_ID, BOTTOM_ID, TOP_ID))).isFalse();
    }

    @ParameterizedTest
    @MethodSource("invalidStructures")
    void null과_빈_응답과_null_ID와_개수_초과는_거부한다(LlmOutfitDecision decision) {
        assertThat(validator.isValid(decision, input())).isFalse();
    }

    static Stream<LlmOutfitDecision> invalidStructures() {
        return Stream.of(null, new LlmOutfitDecision(null), new LlmOutfitDecision(List.of()),
                new LlmOutfitDecision(Arrays.asList(TOP_ID, null)),
                new LlmOutfitDecision(Collections.nCopies(10, TOP_ID)));
    }

    @ParameterizedTest
    @EnumSource(value = ClothesType.class, names = {"UNDERWEAR", "ETC", "TOP", "SOCKS"})
    void 제외_종류_같은종류_중복_신발없는양말은_거부한다(ClothesType type) {
        UUID extra = UUID.randomUUID();
        var wardrobe = new ArrayList<>(input().wardrobe());
        wardrobe.add(new LlmRecommendationInput.Item(extra, "추가 의상", type, List.of()));

        assertThat(validator.isValid(new LlmOutfitDecision(List.of(TOP_ID, BOTTOM_ID, extra)),
                withWardrobe(wardrobe))).isFalse();
    }

    @Test
    void 의상_종류가_없으면_거부한다() {
        var wardrobe = new ArrayList<>(input().wardrobe());
        wardrobe.set(0, new LlmRecommendationInput.Item(TOP_ID, "셔츠", null, List.of()));

        assertThat(validator.isValid(decision(), withWardrobe(wardrobe))).isFalse();
    }

    @ParameterizedTest
    @MethodSource("outfitCombinations")
    void 기본_조합과_양말의_신발_동반_여부를_검증한다(List<ClothesType> types, boolean expected) {
        var wardrobe = types.stream().map(type -> new LlmRecommendationInput.Item(
                UUID.randomUUID(), "의상", type, List.of())).toList();
        var ids = wardrobe.stream().map(LlmRecommendationInput.Item::clothesId).toList();

        assertThat(validator.isValid(new LlmOutfitDecision(ids), withWardrobe(wardrobe))).isEqualTo(expected);
    }

    static Stream<Arguments> outfitCombinations() {
        return Stream.of(
                Arguments.of(List.of(ClothesType.TOP), false),
                Arguments.of(List.of(ClothesType.BOTTOM), false),
                Arguments.of(List.of(ClothesType.OUTER), false),
                Arguments.of(List.of(ClothesType.TOP, ClothesType.BOTTOM, ClothesType.DRESS), false),
                Arguments.of(List.of(ClothesType.DRESS, ClothesType.TOP), false),
                Arguments.of(List.of(ClothesType.DRESS, ClothesType.BOTTOM), false),
                Arguments.of(List.of(ClothesType.DRESS, ClothesType.SOCKS), false),
                Arguments.of(List.of(ClothesType.DRESS, ClothesType.SOCKS, ClothesType.SHOES), true),
                Arguments.of(List.of(ClothesType.TOP, ClothesType.BOTTOM, ClothesType.SOCKS, ClothesType.SHOES), true),
                Arguments.of(List.of(ClothesType.TOP, ClothesType.BOTTOM, ClothesType.OUTER, ClothesType.SHOES,
                        ClothesType.SOCKS, ClothesType.BAG, ClothesType.HAT, ClothesType.ACCESSORY, ClothesType.SCARF), true));
    }

    private boolean validIds(List<UUID> ids) {
        return validator.isValid(new LlmOutfitDecision(ids), input());
    }

    private LlmRecommendationInput withWardrobe(List<LlmRecommendationInput.Item> wardrobe) {
        return new LlmRecommendationInput(input().weather(), 3, input().previousOutfitIds(), wardrobe, List.of());
    }
}
