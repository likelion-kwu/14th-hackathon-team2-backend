package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.routine.catalog.RoutineCatalog;
import com.likelion.hackathon_be.routine.domain.Routine;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.RoutineRecommendationResponse;
import com.likelion.hackathon_be.routine.dto.VerificationObjectResponse;
import com.likelion.hackathon_be.routine.repository.RoutineRepository;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultRoutineCatalogService implements RoutineCatalogService {

    private static final int MAX_RECOMMENDATION_COUNT = 3;
    private static final Pattern CONSECUTIVE_WHITESPACE = Pattern.compile("\\s+");

    private final CurrentUserProvider currentUserProvider;
    private final RoutineRepository routineRepository;
    private final RoutineCatalog routineCatalog;

    public DefaultRoutineCatalogService(
            CurrentUserProvider currentUserProvider,
            RoutineRepository routineRepository,
            RoutineCatalog routineCatalog
    ) {
        this.currentUserProvider = currentUserProvider;
        this.routineRepository = routineRepository;
        this.routineCatalog = routineCatalog;
    }

    @Override
    public List<VerificationObjectResponse> getVerificationObjects() {
        return routineCatalog.getVerificationObjects().stream()
                .map(object -> new VerificationObjectResponse(object.code(), object.name()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoutineRecommendationResponse> getRecommendations(RoutineCategory category) {
        if (category == null) {
            throw new BusinessException(ErrorCode.INVALID_ROUTINE_CATEGORY);
        }

        Long userId = currentUserProvider.getCurrentUser().id();
        Set<String> activeContents = routineRepository
                .findByUserIdAndCategoryAndDeletedAtIsNullOrderByIdAsc(userId, category)
                .stream()
                .map(Routine::getContent)
                .map(this::normalizeContent)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        return routineCatalog.getRecommendations(category).stream()
                .filter(recommendation -> !activeContents.contains(normalizeContent(recommendation.content())))
                .limit(MAX_RECOMMENDATION_COUNT)
                .map(recommendation -> new RoutineRecommendationResponse(
                        recommendation.code(),
                        recommendation.category().name(),
                        recommendation.content(),
                        recommendation.recommendedVerificationObject()
                ))
                .toList();
    }

    @Override
    public boolean supportsVerificationObject(String code) {
        return code != null && routineCatalog.supportsVerificationObject(code);
    }

    private String normalizeContent(String content) {
        return CONSECUTIVE_WHITESPACE.matcher(content.strip()).replaceAll(" ");
    }
}
