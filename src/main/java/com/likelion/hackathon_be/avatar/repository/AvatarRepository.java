package com.likelion.hackathon_be.avatar.repository;

import com.likelion.hackathon_be.avatar.domain.Avatar;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AvatarRepository extends JpaRepository<Avatar, Long> {
    boolean existsByUserId(Long userId);

    Optional<Avatar> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select avatar from Avatar avatar where avatar.userId = :userId")
    Optional<Avatar> findByUserIdForUpdate(@Param("userId") Long userId);

    @Transactional(readOnly = true)
    @Query("select avatar.assetSetKey from Avatar avatar")
    List<String> findAllAssetSetKeys();
}
