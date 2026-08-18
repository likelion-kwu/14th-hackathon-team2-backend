package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.routine.dto.DailyRoutineListResponse;
import java.time.LocalDate;

public interface DailyRoutineService {

    DailyRoutineListResponse getDailyRoutines(LocalDate date);
}
