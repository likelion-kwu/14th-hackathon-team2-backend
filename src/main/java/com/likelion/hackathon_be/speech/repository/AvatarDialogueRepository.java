package com.likelion.hackathon_be.speech.repository;

import com.likelion.hackathon_be.speech.domain.AvatarDialogue;
import com.likelion.hackathon_be.speech.domain.DialogueSituation;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvatarDialogueRepository extends JpaRepository<AvatarDialogue, Long> {
    List<AvatarDialogue> findAllByProfileId(Long profileId);

    @Modifying
    @Query("delete from AvatarDialogue dialogue where dialogue.profileId = :profileId")
    void deleteAllByProfileId(@Param("profileId") Long profileId);

    long countByProfileId(Long profileId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AvatarDialogue> findAllByProfileIdAndSituation(Long profileId, DialogueSituation situation);
}
