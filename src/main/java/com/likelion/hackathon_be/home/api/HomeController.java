package com.likelion.hackathon_be.home.api;

import com.likelion.hackathon_be.common.api.ApiResponse;
import com.likelion.hackathon_be.home.application.HomeService;
import com.likelion.hackathon_be.home.dto.HomeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping
    public ApiResponse<HomeResponse> getHome() {
        return ApiResponse.of(homeService.getHome());
    }
}
