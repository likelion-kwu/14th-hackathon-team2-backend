package com.likelion.hackathon_be.user.application;

import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.user.dto.CurrentUserResponse;
import com.likelion.hackathon_be.user.dto.UpdateUserRequest;
import com.likelion.hackathon_be.user.dto.UpdateUserResponse;
import org.springframework.stereotype.Service;

@Service
public class NotImplementedUserService implements UserService {

    @Override
    public CurrentUserResponse getMe() {
        throw new FeatureNotImplementedException("User");
    }

    @Override
    public UpdateUserResponse updateMe(UpdateUserRequest request) {
        throw new FeatureNotImplementedException("User");
    }
}
