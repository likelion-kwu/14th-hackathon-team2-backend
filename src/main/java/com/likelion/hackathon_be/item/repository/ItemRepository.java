package com.likelion.hackathon_be.item.repository;

import com.likelion.hackathon_be.item.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {
}
