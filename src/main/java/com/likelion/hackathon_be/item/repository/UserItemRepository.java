package com.likelion.hackathon_be.item.repository;

import com.likelion.hackathon_be.item.domain.UserItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserItemRepository extends JpaRepository<UserItem, Long> {
}
