package com.likelion.hackathon_be.speech.repository;

import com.likelion.hackathon_be.speech.domain.SpeechAnalysisJob;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpeechAnalysisJobRepository extends JpaRepository<SpeechAnalysisJob, UUID> {
}
