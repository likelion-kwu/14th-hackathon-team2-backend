package com.likelion.hackathon_be.speech.application;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.speech.domain.SpeechAnalysisJob;
import com.likelion.hackathon_be.speech.domain.SpeechAnalysisJobStatus;
import com.likelion.hackathon_be.speech.infrastructure.KakaoTemporaryStore;
import com.likelion.hackathon_be.speech.repository.SpeechAnalysisJobRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpeechAnalysisJobInvalidator {
    private static final Set<SpeechAnalysisJobStatus> NON_TERMINAL = EnumSet.of(
            SpeechAnalysisJobStatus.UPLOADED,
            SpeechAnalysisJobStatus.WAITING_PARTICIPANT_SELECTION,
            SpeechAnalysisJobStatus.PREPROCESSING,
            SpeechAnalysisJobStatus.ANALYZING,
            SpeechAnalysisJobStatus.GENERATING_DIALOGUES
    );

    private final SpeechAnalysisJobRepository jobRepository;
    private final KakaoTemporaryStore temporaryStore;
    private final TimeProvider timeProvider;
    private final TransactionTemplate transactionTemplate;

    public SpeechAnalysisJobInvalidator(
            SpeechAnalysisJobRepository jobRepository,
            KakaoTemporaryStore temporaryStore,
            TimeProvider timeProvider,
            TransactionTemplate transactionTemplate
    ) {
        this.jobRepository = jobRepository;
        this.temporaryStore = temporaryStore;
        this.timeProvider = timeProvider;
        this.transactionTemplate = transactionTemplate;
    }

    public void invalidateUnfinished(Long userId) {
        List<UUID> invalidated = transactionTemplate.execute(status -> {
            List<SpeechAnalysisJob> jobs = jobRepository.findAllByUserIdAndStatusIn(userId, NON_TERMINAL);
            jobs.forEach(job -> job.transitionTo(SpeechAnalysisJobStatus.EXPIRED, timeProvider.now()));
            return jobs.stream().map(SpeechAnalysisJob::getId).toList();
        });
        if (invalidated != null) {
            invalidated.forEach(temporaryStore::delete);
        }
    }
}
