package com.likelion.hackathon_be.routine.verification.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PhotoVerificationAnalyzerConfiguration {

    @Bean
    @ConditionalOnMissingBean(PhotoVerificationAnalyzer.class)
    PhotoVerificationAnalyzer unavailablePhotoVerificationAnalyzer() {
        return new UnavailablePhotoVerificationAnalyzer();
    }
}
