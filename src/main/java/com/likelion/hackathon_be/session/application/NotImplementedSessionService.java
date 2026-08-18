package com.likelion.hackathon_be.session.application;

import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.session.dto.CreateSessionResponse;

public class NotImplementedSessionService implements SessionService {

    @Override
    public CreateSessionResponse createSession() {
        throw new FeatureNotImplementedException("Session");
    }
}
