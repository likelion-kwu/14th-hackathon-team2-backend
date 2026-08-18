package com.likelion.hackathon_be.session.api;

import com.likelion.hackathon_be.common.api.ApiResponse;
import com.likelion.hackathon_be.session.application.SessionService;
import com.likelion.hackathon_be.session.dto.CreateSessionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateSessionResponse> createSession() {
        return ApiResponse.of(sessionService.createSession());
    }
}
