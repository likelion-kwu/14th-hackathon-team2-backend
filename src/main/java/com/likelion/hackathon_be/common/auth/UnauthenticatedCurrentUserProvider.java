package com.likelion.hackathon_be.common.auth;

import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;

public class UnauthenticatedCurrentUserProvider implements CurrentUserProvider {

    @Override
    public CurrentUser getCurrentUser() {
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
