package com.likelion.hackathon_be.home.application;

import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.home.dto.HomeResponse;
import org.springframework.stereotype.Service;

@Service
public class NotImplementedHomeService implements HomeService {

    @Override
    public HomeResponse getHome() {
        throw new FeatureNotImplementedException("Home");
    }
}
