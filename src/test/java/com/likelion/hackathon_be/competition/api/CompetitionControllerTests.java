package com.likelion.hackathon_be.competition.api;

import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.competition.dto.CompetitionLeaderboardResponse;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompetitionControllerTests {

    @Test
    void parsesExplicitMonth() {
        CompetitionController controller = new CompetitionController(month -> new CompetitionLeaderboardResponse(
                month.toString(),
                List.of(),
                null,
                0
        ));

        assertThat(controller.getLeaderboard("2026-08").data().month()).isEqualTo("2026-08");
    }

    @Test
    void passesNullWhenMonthIsOmitted() {
        CompetitionController controller = new CompetitionController(month -> new CompetitionLeaderboardResponse(
                month == null ? "default" : month.toString(),
                List.of(),
                null,
                0
        ));

        assertThat(controller.getLeaderboard(null).data().month()).isEqualTo("default");
    }

    @Test
    void invalidMonthBecomesValidationError() {
        CompetitionController controller = new CompetitionController((YearMonth month) -> new CompetitionLeaderboardResponse(
                "unused",
                List.of(),
                null,
                0
        ));

        assertThatThrownBy(() -> controller.getLeaderboard("2026-8"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
