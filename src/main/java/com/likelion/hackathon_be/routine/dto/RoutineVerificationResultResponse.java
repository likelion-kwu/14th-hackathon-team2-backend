package com.likelion.hackathon_be.routine.dto;

public record RoutineVerificationResultResponse(
        VerificationResponse verification,
        VerifiedDailyRoutineResponse dailyRoutine,
        DayResultResponse dayResult,
        SuccessSummaryResponse successSummary,
        VerificationPointClaimResponse pointClaim,
        VerificationUnlocksResponse unlocks,
        VerificationDialogueResponse dialogue
) {
}
