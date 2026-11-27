package com.otboo.team03.recommendation.client.dto;

import com.otboo.team03.clothes.entity.ClothesType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RecentCoreOutfitTest {
    private LlmRecommendationInput.Item item(ClothesType type) {
        return new LlmRecommendationInput.Item(UUID.randomUUID(), type.name(), type, List.of());
    }
    @Test void 신발_아우터만_바꾼_기록은_같은_기본_코디로_취급한다() {
        var top = item(ClothesType.TOP); var bottom = item(ClothesType.BOTTOM); var dress = item(ClothesType.DRESS);
        var coat = item(ClothesType.OUTER); var shoes = item(ClothesType.SHOES);
        var a = Set.of(top.clothesId(), bottom.clothesId(), coat.clothesId());
        var b = Set.of(top.clothesId(), bottom.clothesId(), shoes.clothesId());
        var c = Set.of(dress.clothesId(), shoes.clothesId());
        var input = new LlmRecommendationInput(new LlmRecommendationInput.WeatherContext(null, null, null, null, null, null, null),
                3, a, List.of(top, bottom, dress, coat, shoes), List.of(a, b, c));
        assertThat(input.recentCoreOutfitIds()).containsExactly(Set.of(top.clothesId(), bottom.clothesId()), Set.of(dress.clothesId()));
        assertThat(input.previousOutfitIds()).isEqualTo(a);
    }
    @Test void 삭제되거나_다른_사용자_ID와_불완전_기록은_보내지_않는다() {
        var top = item(ClothesType.TOP); var bottom = item(ClothesType.BOTTOM); var dress = item(ClothesType.DRESS);
        var input = new LlmRecommendationInput(new LlmRecommendationInput.WeatherContext(null, null, null, null, null, null, null),
                3, Set.of(), List.of(top, bottom, dress), List.of(Set.of(top.clothesId(), UUID.randomUUID()),
                Set.of(top.clothesId(), bottom.clothesId(), dress.clothesId()), Set.of(UUID.randomUUID()), Set.of(dress.clothesId())));
        assertThat(input.recentCoreOutfitIds()).containsExactly(Set.of(dress.clothesId()));
    }
    @Test void 최근_다섯_기본코디까지만_입력하고_복사해_보관한다() {
        List<LlmRecommendationInput.Item> items = new ArrayList<>(); List<Set<UUID>> recent = new ArrayList<>();
        for (int index = 0; index < 7; index++) { var dress = item(ClothesType.DRESS); items.add(dress); recent.add(Set.of(dress.clothesId())); }
        var input = new LlmRecommendationInput(new LlmRecommendationInput.WeatherContext(null, null, null, null, null, null, null),
                3, Set.of(), items, recent);
        assertThat(input.recentCoreOutfitIds()).containsExactlyElementsOf(recent.subList(0, 5));
        recent.clear(); items.clear();
        assertThat(input.recentCoreOutfitIds()).hasSize(5);
        assertThat(input.wardrobe()).hasSize(7);
    }
}
