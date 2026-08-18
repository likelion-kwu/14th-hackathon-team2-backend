package com.likelion.hackathon_be.user.application;

import com.likelion.hackathon_be.user.dto.CurrentUserResponse;
import com.likelion.hackathon_be.user.dto.UpdateUserRequest;
import com.likelion.hackathon_be.user.dto.UpdateUserResponse;

public interface UserService {

    CurrentUserResponse getMe();

    UpdateUserResponse updateMe(UpdateUserRequest request);
}
