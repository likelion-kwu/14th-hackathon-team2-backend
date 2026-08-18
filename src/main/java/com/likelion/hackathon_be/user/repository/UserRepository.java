package com.likelion.hackathon_be.user.repository;

import com.likelion.hackathon_be.user.domain.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select userEntity from User userEntity where userEntity.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);
}
