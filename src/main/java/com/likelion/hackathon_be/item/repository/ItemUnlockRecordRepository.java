package com.likelion.hackathon_be.item.repository;

import com.likelion.hackathon_be.item.domain.ItemUnlockRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemUnlockRecordRepository extends JpaRepository<ItemUnlockRecord, Long> {

    @Query("""
            select record.requiredPoints
            from ItemUnlockRecord record
            where record.userId = :userId
              and record.requiredPoints <= :highestRequiredPoints
            """)
    List<Integer> findRequiredPointsByUserIdAndRequiredPointsLessThanEqual(
            @Param("userId") Long userId,
            @Param("highestRequiredPoints") int highestRequiredPoints
    );
}
