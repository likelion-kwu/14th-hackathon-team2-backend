package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.auth.CurrentUser;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.daily.repository.DailyRoutineRepository;
import com.likelion.hackathon_be.routine.domain.RepeatType;
import com.likelion.hackathon_be.routine.domain.Routine;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.PhotoMissionResponse;
import com.likelion.hackathon_be.routine.repository.PhotoMissionTemplateRepository;
import com.likelion.hackathon_be.routine.repository.RoutineRepository;
import com.likelion.hackathon_be.user.domain.User;
import com.likelion.hackathon_be.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.avatar.storage-root=${java.io.tmpdir}/godsaeng-lion/test-avatar"
})
@Testcontainers(disabledWithoutDocker = true)
class PhotoMissionPersistenceIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-08-19T00:30:00Z");
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 8, 19);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoutineRepository routineRepository;

    @Autowired
    DailyRoutineRepository dailyRoutineRepository;

    @Autowired
    PhotoMissionTemplateRepository templateRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void flywaySeedsApprovedActivePhotoMissionTemplates() {
        List<String> activeGestureCodes = templateRepository.findByActiveTrueOrderByIdAsc()
                .stream()
                .map(template -> template.getGestureCode())
                .toList();

        assertThat(activeGestureCodes)
                .containsExactly("THUMBS_UP", "V_SIGN");

        List<String> instructions = templateRepository.findByActiveTrueOrderByIdAsc()
                .stream()
                .map(template -> template.getInstructionTemplate())
                .toList();

        assertThat(instructions)
                .containsExactly(
                        "인증 물건과 함께 엄지척 해주세요.",
                        "인증 물건과 함께 브이 포즈를 해주세요."
                );
    }

    @Test
    void seededTemplatesAllowMissionAssignmentWithoutManualFixture() {
        User user = userRepository.saveAndFlush(User.createGuest(NOW));
        Routine routine = routineRepository.saveAndFlush(Routine.create(
                user.getId(),
                RoutineCategory.WELL_BEING,
                "물 마시기",
                LocalTime.of(18, 0),
                LocalTime.of(20, 0),
                RepeatType.DAILY,
                "CUP",
                SERVICE_DATE,
                NOW
        ));
        DailyRoutine dailyRoutine = dailyRoutineRepository.saveAndFlush(
                DailyRoutine.createSnapshot(routine, SERVICE_DATE, NOW)
        );
        DefaultPhotoMissionService service = new DefaultPhotoMissionService(
                () -> new CurrentUser(user.getId()),
                userRepository,
                dailyRoutineRepository,
                templateRepository,
                new PhotoMissionSelector(),
                new FixedTimeProvider(NOW)
        );

        PhotoMissionResponse response = transactionTemplate.execute(status ->
                service.preparePhotoMission(dailyRoutine.getId()));

        assertThat(response).isNotNull();
        assertThat(response.verificationObject()).isEqualTo("CUP");
        assertThat(response.mission().gestureCode()).isIn("THUMBS_UP", "V_SIGN");
        assertThat(response.mission().instruction())
                .startsWith("인증 물건과 함께");
        assertThat(dailyRoutineRepository.findById(dailyRoutine.getId()).orElseThrow().getMissionTemplateId())
                .isNotNull();
    }

    @Test
    void concurrentRequestsPersistAndReturnOneMission() throws Exception {
        User user = userRepository.saveAndFlush(User.createGuest(NOW));
        Routine routine = routineRepository.saveAndFlush(Routine.create(
                user.getId(),
                RoutineCategory.WELL_BEING,
                "물 마시기",
                LocalTime.of(18, 0),
                LocalTime.of(20, 0),
                RepeatType.DAILY,
                "CUP",
                SERVICE_DATE,
                NOW
        ));
        jdbcTemplate.update(
                """
                        insert into photo_mission_templates
                            (gesture_code, instruction_template, active, created_at)
                        values (?, ?, true, ?)
                        """,
                "THUMBS_UP",
                "컵과 함께 엄지척 해주세요.",
                Timestamp.from(NOW)
        );
        DailyRoutine dailyRoutine = dailyRoutineRepository.saveAndFlush(
                DailyRoutine.createSnapshot(routine, SERVICE_DATE, NOW)
        );
        DefaultPhotoMissionService service = new DefaultPhotoMissionService(
                () -> new CurrentUser(user.getId()),
                userRepository,
                dailyRoutineRepository,
                templateRepository,
                new PhotoMissionSelector(),
                new FixedTimeProvider(NOW)
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PhotoMissionResponse> first = executor.submit(() -> prepareInTransaction(
                    service,
                    dailyRoutine.getId(),
                    ready,
                    start
            ));
            Future<PhotoMissionResponse> second = executor.submit(() -> prepareInTransaction(
                    service,
                    dailyRoutine.getId(),
                    ready,
                    start
            ));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            PhotoMissionResponse firstResponse = first.get(10, TimeUnit.SECONDS);
            PhotoMissionResponse secondResponse = second.get(10, TimeUnit.SECONDS);

            assertThat(firstResponse.mission().templateId())
                    .isEqualTo(secondResponse.mission().templateId());
            assertThat(dailyRoutineRepository.findById(dailyRoutine.getId()).orElseThrow().getMissionTemplateId())
                    .isEqualTo(firstResponse.mission().templateId());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private PhotoMissionResponse prepareInTransaction(
            DefaultPhotoMissionService service,
            Long dailyRoutineId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent photo mission requests did not start together");
        }
        return transactionTemplate.execute(status -> service.preparePhotoMission(dailyRoutineId));
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
