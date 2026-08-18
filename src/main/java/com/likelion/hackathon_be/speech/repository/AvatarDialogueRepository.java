package com.likelion.hackathon_be.speech.repository;

import com.likelion.hackathon_be.speech.domain.AvatarDialogue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvatarDialogueRepository extends JpaRepository<AvatarDialogue, Long> {
}
