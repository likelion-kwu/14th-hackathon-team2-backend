package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.auth.CurrentUser;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.daily.repository.DailyRoutineRepository;
import com.likelion.hackathon_be.routine.domain.PhotoMissionTemplate;
import com.likelion.hackathon_be.routine.domain.RepeatType;
import com.likelion.hackathon_be.routine.domain.Routine;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.PhotoMissionResponse;
import com.likelion.hackathon_be.routine.repository.PhotoMissionTemplateRepository;
import com.likelion.hackathon_be.user.domain.User;
import com.likelion.hackathon_be.user.repository.UserRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhotoMissionServiceTests {

    private static final Long USER_ID = 10L;
    private static final Long DAILY_ROUTINE_ID = 101L;
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 8, 19);
    private static final Instant NOW = SERVICE_DATE.atTime(9, 30)
            .atZone(ZoneId.of("Asia/Seoul"))
            .toInstant();

    private UserRepository userRepository;
    private DailyRoutineRepository dailyRoutineRepository;
    private PhotoMissionTemplateRepository templateRepository;
    private PhotoMissionSelector selector;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        dailyRoutineRepository = mock(DailyRoutineRepository.class);
        templateRepository = mock(PhotoMissionTemplateRepository.class);
        selector = mock(PhotoMissionSelector.class);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
    }

    @Test
    void returnsExistingMissionEvenWhenTemplateIsInactive() {
        DailyRoutine dailyRoutine = dailyRoutine(DAILY_ROUTINE_ID, USER_ID, 501L);
        PhotoMissionTemplate existing = template(501L, "THUMBS_UP", "컵과 함께 엄지척 해주세요.", false);
        when(dailyRoutineRepository.findOwnedByIdForUpdate(DAILY_ROUTINE_ID, USER_ID))
                .thenReturn(Optional.of(dailyRoutine));
        when(templateRepository.findById(501L)).thenReturn(Optional.of(existing));

        PhotoMissionResponse response = service().preparePhotoMission(DAILY_ROUTINE_ID);

        assertThat(response.dailyRoutineId()).isEqualTo(DAILY_ROUTINE_ID);
        assertThat(response.verificationObject()).isEqualTo("CUP");
        assertThat(response.mission().templateId()).isEqualTo(501L);
        assertThat(response.mission().gestureCode()).isEqualTo("THUMBS_UP");
        assertThat(response.mission().instruction()).isEqualTo("컵과 함께 엄지척 해주세요.");
        assertThat(response.actualEndAtExclusive().toString()).isEqualTo("2026-08-19T20:01+09:00");
        verify(templateRepository, never()).findByActiveTrueOrderByIdAsc();
        verify(dailyRoutineRepository, never()).save(dailyRoutine);

        InOrder lockOrder = inOrder(userRepository, dailyRoutineRepository);
        lockOrder.verify(userRepository).findByIdForUpdate(USER_ID);
        lockOrder.verify(dailyRoutineRepository).findOwnedByIdForUpdate(DAILY_ROUTINE_ID, USER_ID);
    }

    @Test
    void assignsActiveMissionWhileAvoidingPreviousTemplate() {
        DailyRoutine dailyRoutine = dailyRoutine(DAILY_ROUTINE_ID, USER_ID, null);
        DailyRoutine previous = dailyRoutine(100L, USER_ID, 501L);
        PhotoMissionTemplate first = template(501L, "THUMBS_UP", "thumbs up", true);
        PhotoMissionTemplate selected = template(502L, "V_SIGN", "v sign", true);
        List<PhotoMissionTemplate> activeTemplates = List.of(first, selected);
        when(dailyRoutineRepository.findOwnedByIdForUpdate(DAILY_ROUTINE_ID, USER_ID))
                .thenReturn(Optional.of(dailyRoutine));
        when(templateRepository.findByActiveTrueOrderByIdAsc()).thenReturn(activeTemplates);
        when(dailyRoutineRepository
                .findFirstByUserIdAndIdNotAndMissionTemplateIdIsNotNullOrderByUpdatedAtDescIdDesc(
                        USER_ID,
                        DAILY_ROUTINE_ID
                ))
                .thenReturn(Optional.of(previous));
        when(selector.select(activeTemplates, 501L)).thenReturn(selected);

        PhotoMissionResponse response = service().preparePhotoMission(DAILY_ROUTINE_ID);

        assertThat(response.mission().templateId()).isEqualTo(502L);
        assertThat(dailyRoutine.getMissionTemplateId()).isEqualTo(502L);
        assertThat(dailyRoutine.getUpdatedAt()).isEqualTo(NOW);
        verify(dailyRoutineRepository).save(dailyRoutine);
        verify(selector).select(activeTemplates, 501L);
    }

    @Test
    void repeatedRequestReturnsTheMissionAssignedByTheFirstRequest() {
        DailyRoutine dailyRoutine = dailyRoutine(DAILY_ROUTINE_ID, USER_ID, null);
        PhotoMissionTemplate selected = template(502L, "V_SIGN", "v sign", true);
        when(dailyRoutineRepository.findOwnedByIdForUpdate(DAILY_ROUTINE_ID, USER_ID))
                .thenReturn(Optional.of(dailyRoutine));
        when(templateRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(selected));
        when(selector.select(List.of(selected), null)).thenReturn(selected);
        when(templateRepository.findById(502L)).thenReturn(Optional.of(selected));

        PhotoMissionResponse first = service().preparePhotoMission(DAILY_ROUTINE_ID);
        PhotoMissionResponse second = service().preparePhotoMission(DAILY_ROUTINE_ID);

        assertThat(first).isEqualTo(second);
        verify(selector).select(List.of(selected), null);
        verify(dailyRoutineRepository).save(dailyRoutine);
    }

    @Test
    void hidesMissingOrOtherUsersDailyRoutineAsNotFound() {
        when(dailyRoutineRepository.findOwnedByIdForUpdate(DAILY_ROUTINE_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertBusinessError(
                () -> service().preparePhotoMission(DAILY_ROUTINE_ID),
                ErrorCode.DAILY_ROUTINE_NOT_FOUND
        );

        verify(templateRepository, never()).findByActiveTrueOrderByIdAsc();
    }

    @Test
    void rejectsAssignmentWhenNoActiveTemplateExists() {
        DailyRoutine dailyRoutine = dailyRoutine(DAILY_ROUTINE_ID, USER_ID, null);
        when(dailyRoutineRepository.findOwnedByIdForUpdate(DAILY_ROUTINE_ID, USER_ID))
                .thenReturn(Optional.of(dailyRoutine));
        when(templateRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of());

        assertBusinessError(
                () -> service().preparePhotoMission(DAILY_ROUTINE_ID),
                ErrorCode.PHOTO_MISSION_NOT_PREPARED
        );

        verify(selector, never()).select(List.of(), null);
        verify(dailyRoutineRepository, never()).save(dailyRoutine);
    }

    @Test
    void rejectsBrokenExistingMissionReference() {
        DailyRoutine dailyRoutine = dailyRoutine(DAILY_ROUTINE_ID, USER_ID, 999L);
        when(dailyRoutineRepository.findOwnedByIdForUpdate(DAILY_ROUTINE_ID, USER_ID))
                .thenReturn(Optional.of(dailyRoutine));
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());

        assertBusinessError(
                () -> service().preparePhotoMission(DAILY_ROUTINE_ID),
                ErrorCode.PHOTO_MISSION_NOT_PREPARED
        );
    }

    private DefaultPhotoMissionService service() {
        return new DefaultPhotoMissionService(
                () -> new CurrentUser(USER_ID),
                userRepository,
                dailyRoutineRepository,
                templateRepository,
                selector,
                new FixedTimeProvider(NOW)
        );
    }

    private DailyRoutine dailyRoutine(Long id, Long userId, Long missionTemplateId) {
        Routine routine = Routine.create(
                userId,
                RoutineCategory.WELL_BEING,
                "물 마시기",
                LocalTime.of(18, 0),
                LocalTime.of(20, 0),
                RepeatType.DAILY,
                "CUP",
                SERVICE_DATE,
                NOW
        );
        setField(routine, "id", id + 1_000);
        DailyRoutine dailyRoutine = DailyRoutine.createSnapshot(routine, SERVICE_DATE, NOW.minusSeconds(60));
        setField(dailyRoutine, "id", id);
        setField(dailyRoutine, "missionTemplateId", missionTemplateId);
        return dailyRoutine;
    }

    private PhotoMissionTemplate template(
            Long id,
            String gestureCode,
            String instruction,
            boolean active
    ) {
        PhotoMissionTemplate template = newInstance(PhotoMissionTemplate.class);
        setField(template, "id", id);
        setField(template, "gestureCode", gestureCode);
        setField(template, "instructionTemplate", instruction);
        setField(template, "active", active);
        setField(template, "createdAt", NOW);
        return template;
    }

    private User user(Long id) {
        User user = User.createGuest(NOW);
        setField(user, "id", id);
        return user;
    }

    private void assertBusinessError(ThrowingRunnable runnable, ErrorCode errorCode) {
        assertThatThrownBy(runnable::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
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

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run();
    }

    private record FixedTimeProvider(Instant now) implements TimeProvider {

        @Override
        public LocalDate todayServiceDate() {
            return LocalDate.ofInstant(now, serviceZone());
        }

        @Override
        public ZoneId serviceZone() {
            return ZoneId.of("Asia/Seoul");
        }
    }
}
