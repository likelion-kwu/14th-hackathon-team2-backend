package com.likelion.hackathon_be.routine.catalog;

import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import java.util.List;

public interface RoutineCatalog {

    List<VerificationObjectDefinition> getVerificationObjects();

    List<RoutineRecommendationDefinition> getRecommendations(RoutineCategory category);

    boolean supportsVerificationObject(String code);

    record VerificationObjectDefinition(
            String code,
            String name
    ) {
    }

    record RoutineRecommendationDefinition(
            String code,
            RoutineCategory category,
            String content,
            String recommendedVerificationObject
    ) {
    }
}
