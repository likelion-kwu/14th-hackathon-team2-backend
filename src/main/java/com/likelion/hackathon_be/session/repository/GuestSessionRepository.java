package com.likelion.hackathon_be.session.repository;

import com.likelion.hackathon_be.session.domain.GuestSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestSessionRepository extends JpaRepository<GuestSession, UUID> {

    Optional<GuestSession> findByTokenHash(String tokenHash);
}
