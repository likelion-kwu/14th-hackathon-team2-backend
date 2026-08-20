package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.RoutineRecommendationResponse;
import com.likelion.hackathon_be.routine.dto.VerificationObjectResponse;
import java.util.List;

public interface RoutineCatalogService {

    List<VerificationObjectResponse> getVerificationObjects();

    List<RoutineRecommendationResponse> getRecommendations(RoutineCategory category);

    boolean supportsVerificationObject(String code);
}
