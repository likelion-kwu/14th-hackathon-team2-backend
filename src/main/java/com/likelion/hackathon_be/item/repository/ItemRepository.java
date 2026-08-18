package com.likelion.hackathon_be.item.repository;

import com.likelion.hackathon_be.item.domain.Item;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findByActiveTrueOrderByIdAsc();

    List<Item> findByActiveTrueAndItemTypeOrderByIdAsc(String itemType);
}
