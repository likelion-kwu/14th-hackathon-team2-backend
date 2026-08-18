package com.likelion.hackathon_be.item.repository;

import com.likelion.hackathon_be.item.domain.ItemUnlockRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemUnlockRecordRepository extends JpaRepository<ItemUnlockRecord, Long> {
}
