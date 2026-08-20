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
    void productionCatalogLoadsApprovedMvpOperationalData() {
        ClasspathRoutineCatalog catalog = new ClasspathRoutineCatalog(objectMapper);

        assertThat(catalog.getVerificationObjects())
                .extracting(RoutineCatalog.VerificationObjectDefinition::code)
                .containsExactly(
                        "CUP",
                        "COSMETIC_CONTAINER",
                        "TOWEL",
                        "TOOTHBRUSH",
                        "SUPPLEMENT_CONTAINER"
                );
        assertThat(catalog.getVerificationObjects())
                .extracting(RoutineCatalog.VerificationObjectDefinition::name)
                .containsExactly("컵", "화장품 용기", "수건", "칫솔", "영양제 용기");

        assertThat(catalog.supportsVerificationObject("CUP")).isTrue();
        assertThat(catalog.supportsVerificationObject("COSMETIC_CONTAINER")).isTrue();
        assertThat(catalog.supportsVerificationObject("TOWEL")).isTrue();
        assertThat(catalog.supportsVerificationObject("TOOTHBRUSH")).isTrue();
        assertThat(catalog.supportsVerificationObject("SUPPLEMENT_CONTAINER")).isTrue();
        assertThat(catalog.supportsVerificationObject("UNKNOWN")).isFalse();

        assertThat(catalog.getRecommendations(RoutineCategory.SKIN))
                .extracting(RoutineCatalog.RoutineRecommendationDefinition::code)
                .containsExactly("SKIN_SUNSCREEN", "SKIN_NIGHT_CARE", "SKIN_TOWEL");
        assertThat(catalog.getRecommendations(RoutineCategory.WELL_BEING))
                .extracting(RoutineCatalog.RoutineRecommendationDefinition::code)
                .containsExactly("WELL_WATER_MORNING", "WELL_BRUSH_NIGHT", "WELL_WATER_BREAK");
        assertThat(catalog.getRecommendations(RoutineCategory.HEALTH_FIT))
                .extracting(RoutineCatalog.RoutineRecommendationDefinition::code)
                .containsExactly("FIT_WATER_BEFORE", "FIT_TOWEL_AFTER", "FIT_WATER_AFTER");
        assertThat(catalog.getRecommendations(RoutineCategory.DIET))
                .extracting(RoutineCatalog.RoutineRecommendationDefinition::code)
                .containsExactly("DIET_WATER_BEFORE_MEAL", "DIET_SUPPLEMENT", "DIET_WATER_NIGHT");
        assertThat(catalog.getRecommendations(RoutineCategory.TO_DO)).isEmpty();

        assertThat(catalog.getRecommendations(RoutineCategory.SKIN))
                .extracting(RoutineCatalog.RoutineRecommendationDefinition::content)
                .containsExactly(
                        "외출 전 선크림 바르기",
                        "저녁 세안 후 스킨케어하기",
                        "세안 후 깨끗한 수건 사용하기"
                );
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
