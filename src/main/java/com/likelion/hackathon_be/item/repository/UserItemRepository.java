package com.likelion.hackathon_be.item.repository;

import com.likelion.hackathon_be.item.domain.UserItem;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserItemRepository extends JpaRepository<UserItem, Long> {
    List<UserItem> findAllByUserId(Long userId);

    List<UserItem> findAllByUserIdAndEquippedTrue(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select userItem from UserItem userItem where userItem.userId = :userId")
    List<UserItem> findAllByUserIdForUpdate(@Param("userId") Long userId);

    @Query("""
            select userItem.itemId
            from UserItem userItem
            where userItem.userId = :userId
            """)
    List<Long> findItemIdsByUserId(@Param("userId") Long userId);
}
