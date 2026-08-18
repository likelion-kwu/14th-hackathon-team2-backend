package com.likelion.hackathon_be.speech.repository;

import com.likelion.hackathon_be.speech.domain.SpeechStyleProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpeechStyleProfileRepository extends JpaRepository<SpeechStyleProfile, Long> {

    boolean existsByUserId(Long userId);
}
