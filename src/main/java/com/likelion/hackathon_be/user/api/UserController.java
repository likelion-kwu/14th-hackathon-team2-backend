package com.likelion.hackathon_be.user.api;

import com.likelion.hackathon_be.common.api.ApiResponse;
import com.likelion.hackathon_be.user.application.UserService;
import com.likelion.hackathon_be.user.dto.CurrentUserResponse;
import com.likelion.hackathon_be.user.dto.UpdateUserRequest;
import com.likelion.hackathon_be.user.dto.UpdateUserResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<CurrentUserResponse> getMe() {
        return ApiResponse.of(userService.getMe());
    }

    @PatchMapping
    public ApiResponse<UpdateUserResponse> updateMe(@Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.of(userService.updateMe(request));
    }
}
