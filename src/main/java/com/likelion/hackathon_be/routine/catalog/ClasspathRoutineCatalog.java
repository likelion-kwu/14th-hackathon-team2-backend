package com.likelion.hackathon_be.routine.catalog;

import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ClasspathRoutineCatalog implements RoutineCatalog {

    static final String CATALOG_PATH = "catalog/routine-catalog.json";

    private static final int MAX_VERIFICATION_OBJECT_CODE_LENGTH = 40;
    private static final int MAX_ROUTINE_CONTENT_LENGTH = 100;

    private final List<VerificationObjectDefinition> verificationObjects;
    private final Map<RoutineCategory, List<RoutineRecommendationDefinition>> recommendationsByCategory;
    private final Set<String> verificationObjectCodes;

    public ClasspathRoutineCatalog(ObjectMapper objectMapper) {
        this(objectMapper, new ClassPathResource(CATALOG_PATH));
    }

    ClasspathRoutineCatalog(ObjectMapper objectMapper, Resource resource) {
        CatalogDocument document = load(objectMapper, resource);
        validate(document);

        this.verificationObjects = List.copyOf(document.verificationObjects());
        this.verificationObjectCodes = this.verificationObjects.stream()
                .map(VerificationObjectDefinition::code)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        EnumMap<RoutineCategory, List<RoutineRecommendationDefinition>> grouped =
                new EnumMap<>(RoutineCategory.class);
        for (RoutineCategory category : RoutineCategory.values()) {
            grouped.put(category, document.recommendations().stream()
                    .filter(recommendation -> recommendation.category() == category)
                    .toList());
        }
        this.recommendationsByCategory = Map.copyOf(grouped);
    }

    @Override
    public List<VerificationObjectDefinition> getVerificationObjects() {
        return verificationObjects;
    }

    @Override
    public List<RoutineRecommendationDefinition> getRecommendations(RoutineCategory category) {
        if (category == null) {
            return List.of();
        }
        return recommendationsByCategory.getOrDefault(category, List.of());
    }

    @Override
    public boolean supportsVerificationObject(String code) {
        return code != null && verificationObjectCodes.contains(code);
    }

    private CatalogDocument load(ObjectMapper objectMapper, Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, CatalogDocument.class);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Routine catalog resource could not be loaded", exception);
        }
    }

    private void validate(CatalogDocument document) {
        if (document == null || document.verificationObjects() == null || document.recommendations() == null) {
            throw invalidCatalog("catalog lists must not be null");
        }

        Set<String> objectCodes = new HashSet<>();
        for (VerificationObjectDefinition object : document.verificationObjects()) {
            if (object == null
                    || isBlank(object.code())
                    || object.code().length() > MAX_VERIFICATION_OBJECT_CODE_LENGTH
                    || isBlank(object.name())) {
                throw invalidCatalog("verification object fields are invalid");
            }
            if (!objectCodes.add(object.code())) {
                throw invalidCatalog("verification object codes must be unique");
            }
        }

        Set<String> recommendationCodes = new HashSet<>();
        for (RoutineRecommendationDefinition recommendation : document.recommendations()) {
            if (recommendation == null
                    || isBlank(recommendation.code())
                    || recommendation.category() == null
                    || isBlank(recommendation.content())
                    || recommendation.content().length() > MAX_ROUTINE_CONTENT_LENGTH
                    || isBlank(recommendation.recommendedVerificationObject())) {
                throw invalidCatalog("routine recommendation fields are invalid");
            }
            if (!recommendationCodes.add(recommendation.code())) {
                throw invalidCatalog("routine recommendation codes must be unique");
            }
            if (!objectCodes.contains(recommendation.recommendedVerificationObject())) {
                throw invalidCatalog("routine recommendation references an unsupported verification object");
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private IllegalStateException invalidCatalog(String reason) {
        return new IllegalStateException("Invalid routine catalog: " + reason);
    }

    record CatalogDocument(
            List<VerificationObjectDefinition> verificationObjects,
            List<RoutineRecommendationDefinition> recommendations
    ) {
    }
}
