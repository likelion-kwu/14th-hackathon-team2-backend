package com.likelion.hackathon_be.routine.repository;

import com.likelion.hackathon_be.routine.domain.PhotoMissionTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoMissionTemplateRepository extends JpaRepository<PhotoMissionTemplate, Long> {

    List<PhotoMissionTemplate> findByActiveTrueOrderByIdAsc();
}
