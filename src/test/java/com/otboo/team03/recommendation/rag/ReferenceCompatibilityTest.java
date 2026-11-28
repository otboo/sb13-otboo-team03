package com.otboo.team03.recommendation.rag;

import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput.Attribute;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.*;
import static com.otboo.team03.recommendation.rag.RagTestFixtures.*;

class ReferenceCompatibilityTest {
    private final ReferenceCompatibility filter = new ReferenceCompatibility();

    @Test void 제목과_충돌하면_등록_색상과_핏을_우선한다() throws Exception {
        var input = outfitWithConflictingTitles();
        assertThat(filter.assess(catalog().get("uniqlo-brown-black"), input)).isEqualTo(ReferenceCompatibility.Assessment.MATCH);
        assertThat(filter.assess(catalog().get("musinsa-relaxed"), input)).isEqualTo(ReferenceCompatibility.Assessment.MATCH);
    }
    @Test void 원피스와_가방도_배색에는_적용하지만_상의하의_조합_사례는_제외한다() throws Exception {
        var input = brownDressWithBlackBag();
        assertThat(filter.assess(catalog().get("uniqlo-brown-black"), input)).isEqualTo(ReferenceCompatibility.Assessment.MATCH);
        assertThat(filter.assess(catalog().get("musinsa-shirt-trousers"), input)).isEqualTo(ReferenceCompatibility.Assessment.CONFLICT);
    }
    @Test void 명시적_색상_충돌과_추천_제외_종류는_호환으로_판단하지_않는다() throws Exception {
        var reference = catalog().get("uniqlo-brown-black");
        assertThat(filter.assess(reference, blueShirtAndNavyPants())).isEqualTo(ReferenceCompatibility.Assessment.CONFLICT);
        assertThat(filter.assess(reference, input(List.of(item(ClothesType.UNDERWEAR, "브라운 속옷"),
                item(ClothesType.ETC, "블랙 기타"))))).isEqualTo(ReferenceCompatibility.Assessment.CONFLICT);
    }
    @Test void 부정표현과_알수없는_등록값은_제목으로_덮어쓰지_않는다() throws Exception {
        var input = input(List.of(item(ClothesType.TOP, "블랙 슬림핏 셔츠", new Attribute("색상", "블랙 아님"),
                new Attribute("핏", "오버핏이 아닌 형태")), item(ClothesType.BOTTOM, "네이비 팬츠")));
        assertThat(filter.assess(catalog().get("musinsa-blue-navy-preppy"), input))
                .isEqualTo(ReferenceCompatibility.Assessment.UNKNOWN);
    }
    @Test void 크롭과_브랜드명으로_핏이나_색상을_단정하지_않는다() throws Exception {
        var input = input(List.of(item(ClothesType.TOP, "블랙야크 크롭 셔츠"), item(ClothesType.BOTTOM, "와이드 팬츠")));
        assertThat(filter.assess(catalog().get("uniqlo-wide-compact"), input)).isEqualTo(ReferenceCompatibility.Assessment.UNKNOWN);
    }
    @Test void 한_의상의_색과_다른_의상의_핏을_합쳐_일치시키지_않는다() {
        var ref = new StyleReference("test", "source", "https://example.com", "", "", "summary", Set.of(),
                Map.of(ClothesType.TOP, new StyleReference.Requirement(Set.of("blue"), Set.of("compact"))),
                List.of(Map.of(ClothesType.TOP, 1L)));
        var input = input(List.of(item(ClothesType.TOP, "블루 루즈핏 셔츠"), item(ClothesType.TOP, "블랙 슬림핏 니트")));
        assertThat(filter.assess(ref, input)).isEqualTo(ReferenceCompatibility.Assessment.CONFLICT);
    }
    @Test void 두_상의_대안은_실제_두_벌을_요구한다() throws Exception {
        var rule = catalog().get("uniqlo-mesh-cardigan");
        assertThat(filter.assess(rule, input(List.of(item(ClothesType.TOP, "메시 티")))))
                .isEqualTo(ReferenceCompatibility.Assessment.CONFLICT);
        assertThat(filter.assess(rule, input(List.of(item(ClothesType.TOP, "메시 티"), item(ClothesType.TOP, "가디건")))))
                .isEqualTo(ReferenceCompatibility.Assessment.UNCONSTRAINED);
    }
    @Test void 잘못된_적용_범위와_중복_출처를_초기에_거부한다() throws Exception {
        assertThatThrownBy(() -> StyleReferenceCatalog.parseRoles("TOP+" )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StyleReferenceCatalog.parseRoles("UNDERWEAR")).isInstanceOf(IllegalArgumentException.class);
        var data = new org.springframework.core.io.ClassPathResource("rag/outfit-style-references.tsv").getContentAsByteArray();
        String text = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        String duplicate = text + text.lines().skip(1).findFirst().orElseThrow() + "\n";
        assertThatThrownBy(() -> new StyleReferenceCatalog(new ByteArrayResource(duplicate.getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
