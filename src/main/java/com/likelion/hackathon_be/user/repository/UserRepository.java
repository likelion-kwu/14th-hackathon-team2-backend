package com.likelion.hackathon_be.user.repository;

import com.likelion.hackathon_be.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
