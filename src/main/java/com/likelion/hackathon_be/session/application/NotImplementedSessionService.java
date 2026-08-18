package com.likelion.hackathon_be.session.application;

import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.session.dto.CreateSessionResponse;
import org.springframework.stereotype.Service;

@Service
public class NotImplementedSessionService implements SessionService {

    @Override
    public CreateSessionResponse createSession() {
        throw new FeatureNotImplementedException("Session");
    }
}
