package com.likelion.hackathon_be.routine.api;

import com.likelion.hackathon_be.common.api.ApiResponse;
import com.likelion.hackathon_be.routine.application.RoutineCatalogService;
import com.likelion.hackathon_be.routine.dto.VerificationObjectResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/verification-objects")
public class VerificationObjectController {

    private final RoutineCatalogService routineCatalogService;

    public VerificationObjectController(RoutineCatalogService routineCatalogService) {
        this.routineCatalogService = routineCatalogService;
    }

    @GetMapping
    public ApiResponse<List<VerificationObjectResponse>> getVerificationObjects() {
        return ApiResponse.of(routineCatalogService.getVerificationObjects());
    }
}
