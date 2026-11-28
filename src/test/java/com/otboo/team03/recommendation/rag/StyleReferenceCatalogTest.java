package com.otboo.team03.recommendation.rag;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.*;

class StyleReferenceCatalogTest {
    @ParameterizedTest(name = "잘못된 자료 {0}={1}")
    @CsvSource({
            "id, ''", "summary, ''", "source, ''", "url, http://example.com/style",
            "url, https:/style", "TOP_color, unknown", "BOTTOM_fit, unknown", "palette, unknown",
            "mode, unknown", "required_types, *", "mode, palette", "basis, ''"
    })
    void 잘못된_출처나_적용_조건의_자료는_적재하지_않는다(String column, String value) throws Exception {
        assertThatThrownBy(() -> new StyleReferenceCatalog(rowWith(column, value)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void 요약_길이_상한을_검증한다() throws Exception {
        assertThat(new StyleReferenceCatalog(rowWith("summary", "가".repeat(500))).all()).hasSize(1);
        assertThatThrownBy(() -> new StyleReferenceCatalog(rowWith("summary", "가".repeat(501))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void 빈_자료와_열_누락은_적재하지_않는다() throws Exception {
        var valid = rowWith("id", "test-reference").getContentAsString(StandardCharsets.UTF_8);
        for (String invalid : new String[]{"", valid.lines().findFirst().orElseThrow(),
                valid + "\textra-column", valid.replaceFirst("summary", "missing-column")}) {
            assertThatThrownBy(() -> new StyleReferenceCatalog(resource(invalid)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private ByteArrayResource rowWith(String column, String value) throws Exception {
        var lines = new ClassPathResource("rag/outfit-style-references.tsv")
                .getContentAsString(StandardCharsets.UTF_8).lines().limit(2).toList();
        var headers = Arrays.asList(lines.get(0).split("\t", -1));
        var values = lines.get(1).split("\t", -1);
        values[headers.indexOf(column)] = value;
        return resource(lines.get(0) + "\n" + String.join("\t", values));
    }

    private ByteArrayResource resource(String text) {
        return new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8));
    }
}
