package com.likelion.hackathon_be.routine.catalog;

import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClasspathRoutineCatalogTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loadsValidCatalogEntries() {
        ClasspathRoutineCatalog catalog = new ClasspathRoutineCatalog(objectMapper, jsonResource("""
                {
                  "verificationObjects": [
                    {"code": "CUP", "name": "컵"},
                    {"code": "COSMETIC_CONTAINER", "name": "화장품 용기"}
                  ],
                  "recommendations": [
                    {
                      "code": "SKIN_SUNSCREEN",
                      "category": "SKIN",
                      "content": "외출 전 선크림 바르기",
                      "recommendedVerificationObject": "COSMETIC_CONTAINER"
                    }
                  ]
                }
                """));

        assertThat(catalog.getVerificationObjects())
                .extracting(RoutineCatalog.VerificationObjectDefinition::code)
                .containsExactly("CUP", "COSMETIC_CONTAINER");
        assertThat(catalog.getRecommendations(RoutineCategory.SKIN))
                .singleElement()
                .satisfies(recommendation -> {
                    assertThat(recommendation.code()).isEqualTo("SKIN_SUNSCREEN");
                    assertThat(recommendation.content()).isEqualTo("외출 전 선크림 바르기");
                    assertThat(recommendation.recommendedVerificationObject())
                            .isEqualTo("COSMETIC_CONTAINER");
                });
        assertThat(catalog.getRecommendations(RoutineCategory.WELL_BEING)).isEmpty();
    }

    @Test
    void productionCatalogDoesNotPromoteUnapprovedApiExamplesToOperationalData() {
        ClasspathRoutineCatalog catalog = new ClasspathRoutineCatalog(objectMapper);

        assertThat(catalog.getVerificationObjects()).isEmpty();
        assertThat(catalog.getRecommendations(RoutineCategory.SKIN)).isEmpty();
    }

    @Test
    void rejectsRecommendationThatReferencesUnsupportedVerificationObject() {
        ByteArrayResource resource = jsonResource("""
                {
                  "verificationObjects": [
                    {"code": "CUP", "name": "컵"}
                  ],
                  "recommendations": [
                    {
                      "code": "SKIN_SUNSCREEN",
                      "category": "SKIN",
                      "content": "외출 전 선크림 바르기",
                      "recommendedVerificationObject": "UNKNOWN"
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> new ClasspathRoutineCatalog(objectMapper, resource))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsupported verification object");
    }

    @Test
    void rejectsDuplicateCatalogCodes() {
        ByteArrayResource resource = jsonResource("""
                {
                  "verificationObjects": [
                    {"code": "CUP", "name": "컵"},
                    {"code": "CUP", "name": "다른 컵"}
                  ],
                  "recommendations": []
                }
                """);

        assertThatThrownBy(() -> new ClasspathRoutineCatalog(objectMapper, resource))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be unique");
    }

    private ByteArrayResource jsonResource(String json) {
        return new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8));
    }
}
