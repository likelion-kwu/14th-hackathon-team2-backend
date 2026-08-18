package com.likelion.hackathon_be.common.error;

public class FeatureNotImplementedException extends BusinessException {

    public FeatureNotImplementedException() {
        super(ErrorCode.NOT_IMPLEMENTED);
    }

    public FeatureNotImplementedException(String featureName) {
        super(ErrorCode.NOT_IMPLEMENTED, featureName + " 기능은 아직 구현되지 않았습니다.");
    }
}
