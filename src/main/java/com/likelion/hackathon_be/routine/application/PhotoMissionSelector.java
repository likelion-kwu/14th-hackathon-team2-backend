package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.routine.domain.PhotoMissionTemplate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class PhotoMissionSelector {

    private final IntUnaryOperator indexPicker;

    public PhotoMissionSelector() {
        this(bound -> ThreadLocalRandom.current().nextInt(bound));
    }

    PhotoMissionSelector(RandomGenerator randomGenerator) {
        this((IntUnaryOperator) randomGenerator::nextInt);
    }

    private PhotoMissionSelector(IntUnaryOperator indexPicker) {
        this.indexPicker = indexPicker;
    }

    public PhotoMissionTemplate select(
            List<PhotoMissionTemplate> activeTemplates,
            Long previousTemplateId
    ) {
        if (activeTemplates == null || activeTemplates.isEmpty()) {
            throw new IllegalArgumentException("At least one active photo mission template is required");
        }

        List<PhotoMissionTemplate> candidates = withoutPreviousWhenPossible(
                activeTemplates,
                previousTemplateId
        );
        return candidates.get(indexPicker.applyAsInt(candidates.size()));
    }

    private List<PhotoMissionTemplate> withoutPreviousWhenPossible(
            List<PhotoMissionTemplate> activeTemplates,
            Long previousTemplateId
    ) {
        if (previousTemplateId == null || activeTemplates.size() == 1) {
            return activeTemplates;
        }

        List<PhotoMissionTemplate> alternatives = activeTemplates.stream()
                .filter(template -> !previousTemplateId.equals(template.getId()))
                .toList();
        return alternatives.isEmpty() ? activeTemplates : alternatives;
    }
}
