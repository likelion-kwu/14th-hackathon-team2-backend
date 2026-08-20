package com.likelion.hackathon_be.speech.repository;

import com.likelion.hackathon_be.speech.domain.SpeechStyleExample;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpeechStyleExampleRepository extends JpaRepository<SpeechStyleExample, Long> {
    List<SpeechStyleExample> findAllByProfileId(Long profileId);

    @Modifying
    @Query("delete from SpeechStyleExample example where example.profileId = :profileId")
    void deleteAllByProfileId(@Param("profileId") Long profileId);
}
