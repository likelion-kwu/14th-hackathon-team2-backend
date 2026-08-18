package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.RoutineRecommendationResponse;
import com.likelion.hackathon_be.routine.dto.VerificationObjectResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NotImplementedRoutineCatalogService implements RoutineCatalogService {

    @Override
    public List<VerificationObjectResponse> getVerificationObjects() {
        throw new FeatureNotImplementedException("Verification objects");
    }

    @Override
    public List<RoutineRecommendationResponse> getRecommendations(RoutineCategory category) {
        throw new FeatureNotImplementedException("Routine recommendations");
    }
}
