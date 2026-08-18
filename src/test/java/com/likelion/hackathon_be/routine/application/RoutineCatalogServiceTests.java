package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.auth.CurrentUser;
import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.routine.catalog.RoutineCatalog;
import com.likelion.hackathon_be.routine.domain.RepeatType;
import com.likelion.hackathon_be.routine.domain.Routine;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.RoutineRecommendationResponse;
import com.likelion.hackathon_be.routine.repository.RoutineRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RoutineCatalogServiceTests {

    private static final Long USER_ID = 1001L;

    private CurrentUserProvider currentUserProvider;
    private RoutineRepository routineRepository;

    @BeforeEach
    void setUp() {
        currentUserProvider = mock(CurrentUserProvider.class);
        routineRepository = mock(RoutineRepository.class);
        when(currentUserProvider.getCurrentUser()).thenReturn(new CurrentUser(USER_ID));
    }

    @Test
    void returnsVerificationObjectsWithoutUserOrDatabaseLookup() {
        RoutineCatalog catalog = mock(RoutineCatalog.class);
        when(catalog.getVerificationObjects()).thenReturn(List.of(
                new RoutineCatalog.VerificationObjectDefinition("CUP", "컵"),
                new RoutineCatalog.VerificationObjectDefinition("COSMETIC_CONTAINER", "화장품 용기")
        ));
        DefaultRoutineCatalogService service = service(catalog);

        assertThat(service.getVerificationObjects())
                .extracting(response -> response.code())
                .containsExactly("CUP", "COSMETIC_CONTAINER");
        verifyNoInteractions(routineRepository);
    }

    @Test
    void excludesRecommendationWithSameNormalizedActiveRoutineContent() {
        RoutineCatalog catalog = mock(RoutineCatalog.class);
        when(catalog.getRecommendations(RoutineCategory.SKIN)).thenReturn(List.of(
                new RoutineCatalog.RoutineRecommendationDefinition(
                        "SKIN_SUNSCREEN",
                        RoutineCategory.SKIN,
                        "외출 전 선크림 바르기",
                        "COSMETIC_CONTAINER"
                )
        ));
        DefaultRoutineCatalogService service = service(catalog);
        Routine existingRoutine = Routine.create(
                USER_ID,
                RoutineCategory.SKIN,
                "  외출   전 선크림 바르기  ",
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                RepeatType.DAILY,
                "COSMETIC_CONTAINER",
                LocalDate.of(2026, 8, 19),
                Instant.parse("2026-08-19T00:00:00Z")
        );
        when(routineRepository.findByUserIdAndCategoryAndDeletedAtIsNullOrderByIdAsc(
                USER_ID,
                RoutineCategory.SKIN
        )).thenReturn(List.of(existingRoutine));

        assertThat(service.getRecommendations(RoutineCategory.SKIN)).isEmpty();

        verify(currentUserProvider).getCurrentUser();
        verify(routineRepository).findByUserIdAndCategoryAndDeletedAtIsNullOrderByIdAsc(
                USER_ID,
                RoutineCategory.SKIN
        );
        verifyNoMoreInteractions(routineRepository);
    }

    @Test
    void returnsAtMostThreeRecommendationsForRequestedCategory() {
        RoutineCatalog catalog = mock(RoutineCatalog.class);
        when(catalog.getRecommendations(RoutineCategory.DIET)).thenReturn(List.of(
                recommendation("DIET_1"),
                recommendation("DIET_2"),
                recommendation("DIET_3"),
                recommendation("DIET_4")
        ));
        when(routineRepository.findByUserIdAndCategoryAndDeletedAtIsNullOrderByIdAsc(
                USER_ID,
                RoutineCategory.DIET
        )).thenReturn(List.of());
        DefaultRoutineCatalogService service = service(catalog);

        List<RoutineRecommendationResponse> responses = service.getRecommendations(RoutineCategory.DIET);

        assertThat(responses)
                .extracting(RoutineRecommendationResponse::code)
                .containsExactly("DIET_1", "DIET_2", "DIET_3");
        assertThat(responses).allSatisfy(response -> assertThat(response.category()).isEqualTo("DIET"));
    }

    @Test
    void rejectsMissingCategoryBeforeAuthenticationOrDatabaseLookup() {
        DefaultRoutineCatalogService service = service(mock(RoutineCatalog.class));

        assertThatThrownBy(() -> service.getRecommendations(null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_ROUTINE_CATEGORY));

        verifyNoInteractions(currentUserProvider, routineRepository);
    }

    @Test
    void checksVerificationObjectAgainstSameCatalog() {
        RoutineCatalog catalog = mock(RoutineCatalog.class);
        when(catalog.supportsVerificationObject("COSMETIC_CONTAINER")).thenReturn(true);
        DefaultRoutineCatalogService service = service(catalog);

        assertThat(service.supportsVerificationObject("COSMETIC_CONTAINER")).isTrue();
        assertThat(service.supportsVerificationObject(" COSMETIC_CONTAINER ")).isFalse();
        assertThat(service.supportsVerificationObject("UNKNOWN")).isFalse();
        assertThat(service.supportsVerificationObject(null)).isFalse();
    }

    private DefaultRoutineCatalogService service(RoutineCatalog catalog) {
        return new DefaultRoutineCatalogService(currentUserProvider, routineRepository, catalog);
    }

    private RoutineCatalog.RoutineRecommendationDefinition recommendation(String code) {
        return new RoutineCatalog.RoutineRecommendationDefinition(
                code,
                RoutineCategory.DIET,
                code + " content",
                "CUP"
        );
    }
}
