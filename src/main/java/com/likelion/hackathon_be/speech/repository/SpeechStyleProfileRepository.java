package com.likelion.hackathon_be.speech.repository;

import com.likelion.hackathon_be.speech.domain.SpeechStyleProfile;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpeechStyleProfileRepository extends JpaRepository<SpeechStyleProfile, Long> {
    Optional<SpeechStyleProfile> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from SpeechStyleProfile profile where profile.userId = :userId")
    Optional<SpeechStyleProfile> findByUserIdForUpdate(@Param("userId") Long userId);
}
