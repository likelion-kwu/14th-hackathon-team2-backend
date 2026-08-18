package com.likelion.hackathon_be.speech.repository;

import com.likelion.hackathon_be.speech.domain.SpeechAnalysisJob;
import com.likelion.hackathon_be.speech.domain.SpeechAnalysisJobStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpeechAnalysisJobRepository extends JpaRepository<SpeechAnalysisJob, UUID> {
    List<SpeechAnalysisJob> findAllByStatusIn(Collection<SpeechAnalysisJobStatus> statuses);

    Optional<SpeechAnalysisJob> findByIdAndUserId(UUID id, Long userId);

    List<SpeechAnalysisJob> findAllByUserIdAndStatusIn(
            Long userId,
            Collection<SpeechAnalysisJobStatus> statuses
    );

    Optional<SpeechAnalysisJob> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    List<SpeechAnalysisJob> findAllByStatusInAndUpdatedAtBefore(
            Collection<SpeechAnalysisJobStatus> statuses,
            Instant updatedAt
    );

    List<SpeechAnalysisJob> findAllByStatusInAndExpiresAtLessThanEqual(
            Collection<SpeechAnalysisJobStatus> statuses,
            Instant expiresAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from SpeechAnalysisJob job where job.id = :id and job.userId = :userId")
    Optional<SpeechAnalysisJob> findOwnedForUpdate(@Param("id") UUID id, @Param("userId") Long userId);
}
