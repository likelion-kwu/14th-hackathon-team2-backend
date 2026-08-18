package com.likelion.hackathon_be.avatar.repository;

import com.likelion.hackathon_be.avatar.domain.Avatar;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvatarRepository extends JpaRepository<Avatar, Long> {

    boolean existsByUserId(Long userId);
}
