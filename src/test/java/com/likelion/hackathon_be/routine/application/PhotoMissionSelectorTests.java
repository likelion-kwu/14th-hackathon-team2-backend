package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.routine.domain.PhotoMissionTemplate;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhotoMissionSelectorTests {

    @Test
    void excludesPreviousTemplateWhenAnotherActiveTemplateExists() {
        RandomGenerator randomGenerator = mock(RandomGenerator.class);
        when(randomGenerator.nextInt(2)).thenReturn(1);
        PhotoMissionSelector selector = new PhotoMissionSelector(randomGenerator);

        PhotoMissionTemplate selected = selector.select(
                List.of(template(1L), template(2L), template(3L)),
                2L
        );

        assertThat(selected.getId()).isEqualTo(3L);
        verify(randomGenerator).nextInt(2);
    }

    @Test
    void reusesOnlyTemplateWhenNoAlternativeExists() {
        RandomGenerator randomGenerator = mock(RandomGenerator.class);
        when(randomGenerator.nextInt(1)).thenReturn(0);
        PhotoMissionSelector selector = new PhotoMissionSelector(randomGenerator);

        PhotoMissionTemplate selected = selector.select(List.of(template(7L)), 7L);

        assertThat(selected.getId()).isEqualTo(7L);
        verify(randomGenerator).nextInt(1);
    }

    @Test
    void rejectsEmptyTemplatePool() {
        PhotoMissionSelector selector = new PhotoMissionSelector(mock(RandomGenerator.class));

        assertThatThrownBy(() -> selector.select(List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PhotoMissionTemplate template(Long id) {
        PhotoMissionTemplate template = newInstance(PhotoMissionTemplate.class);
        setField(template, "id", id);
        setField(template, "gestureCode", "GESTURE_" + id);
        setField(template, "instructionTemplate", "instruction-" + id);
        setField(template, "active", true);
        setField(template, "createdAt", Instant.parse("2026-08-19T00:00:00Z"));
        return template;
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
