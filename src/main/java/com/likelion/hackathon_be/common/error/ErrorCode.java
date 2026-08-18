package com.likelion.hackathon_be.common.error;

public enum ErrorCode {
    VALIDATION_ERROR(400, "요청값을 확인해 주세요."),
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다."),
    NOT_IMPLEMENTED(501, "아직 구현되지 않은 기능입니다."),

    UNAUTHORIZED(401, "인증이 필요합니다."),
    ONBOARDING_INCOMPLETE(409, "온보딩을 먼저 완료해 주세요."),
    NICKNAME_REQUIRED(409, "닉네임을 먼저 설정해 주세요."),
    AVATAR_NOT_CONFIGURED(409, "아바타를 먼저 설정해 주세요."),
    SPEECH_STYLE_NOT_CONFIGURED(409, "말투를 먼저 설정해 주세요."),
    AVATAR_TRACK_REQUIRED(400, "아바타 성장 트랙을 선택해 주세요."),
    AVATAR_TRACK_LOCKED(409, "아바타 성장 트랙은 변경할 수 없습니다."),
    AVATAR_FACE_PHOTO_INVALID(422, "얼굴 사진을 확인해 주세요."),
    AVATAR_REGENERATION_LIMIT_REACHED(409, "아바타 재생성 가능 횟수를 모두 사용했습니다."),
    AVATAR_IMAGE_NOT_FOUND(404, "아바타 이미지를 찾을 수 없습니다."),
    AVATAR_GENERATION_FAILED(503, "아바타 생성에 실패했습니다."),

    ROUTINE_NOT_FOUND(404, "루틴을 찾을 수 없습니다."),
    DAILY_ROUTINE_NOT_FOUND(404, "오늘의 루틴을 찾을 수 없습니다."),
    INVALID_REPEAT_DAYS(400, "반복 요일을 확인해 주세요."),
    INVALID_TIME_RANGE(400, "루틴 시간을 확인해 주세요."),
    VERIFICATION_OBJECT_NOT_SUPPORTED(400, "지원하지 않는 인증 물건입니다."),
    SERVICE_DATE_LOCKED(409, "해당 날짜의 루틴 목록은 이미 확정되었습니다."),
    INVALID_ROUTINE_CATEGORY(400, "루틴 카테고리를 확인해 주세요."),
    INVALID_REPEAT_TYPE_FOR_CATEGORY(400, "카테고리에 맞는 반복 타입을 선택해 주세요."),
    INVALID_ONCE_DATE(400, "일회성 루틴 날짜를 확인해 주세요."),

    ROUTINE_NOT_STARTED(409, "아직 루틴 수행 시간이 아닙니다."),
    ROUTINE_WINDOW_CLOSED(409, "루틴 수행 시간이 지났습니다."),
    ALREADY_VERIFIED(409, "이미 인증이 완료된 루틴입니다."),
    PHOTO_MISSION_NOT_PREPARED(409, "사진 미션을 먼저 준비해 주세요."),
    PHOTO_VERIFICATION_FAILED(422, "사진 인증에 실패했습니다."),
    PHOTO_NOT_DECIDABLE(422, "사진 인증 결과를 판단할 수 없습니다."),
    PHOTO_AI_UNAVAILABLE(503, "사진 인증 서비스를 사용할 수 없습니다."),
    ROUTINE_NOT_COMPLETED(409, "완료된 루틴만 Point를 수령할 수 있습니다."),
    POINT_ALREADY_CLAIMED(409, "이미 Point를 수령한 루틴입니다."),
    POINT_CLAIM_LIMIT_REACHED(409, "오늘 수령 가능한 Point 개수를 초과했습니다."),
    POINT_CLAIM_EXPIRED(409, "Point 수령 가능 시간이 지났습니다."),

    STYLE_NOT_FOUND(404, "활성 말투를 찾을 수 없습니다."),
    PRESET_NOT_FOUND(404, "프리셋을 찾을 수 없습니다."),
    INVALID_FILE_TYPE(415, "파일 형식을 확인해 주세요."),
    ZIP_TOO_LARGE(413, "파일 크기가 너무 큽니다."),
    UNSUPPORTED_ARCHIVE(415, "지원하지 않는 압축 파일입니다."),
    CHAT_TEXT_NOT_FOUND(400, "대화 텍스트 파일을 찾을 수 없습니다."),
    CHAT_FORMAT_UNSUPPORTED(400, "지원하지 않는 대화 형식입니다."),
    PARTICIPANT_NOT_FOUND(404, "참여자를 찾을 수 없습니다."),
    INSUFFICIENT_MESSAGES(422, "분석 가능한 메시지가 부족합니다."),
    AI_ANALYSIS_FAILED(503, "말투 분석에 실패했습니다."),
    AI_RESPONSE_INVALID(503, "말투 분석 응답을 처리할 수 없습니다."),
    DIALOGUE_GENERATION_FAILED(503, "아바타 대사 생성에 실패했습니다."),
    ANALYSIS_JOB_NOT_FOUND(404, "분석 작업을 찾을 수 없습니다."),
    ANALYSIS_EXPIRED(410, "분석 작업이 만료되었습니다."),

    ITEM_NOT_FOUND(404, "아이템을 찾을 수 없습니다."),
    ITEM_NOT_OWNED(409, "보유하지 않은 아이템입니다."),
    INVALID_EQUIPMENT(400, "아이템 장착 상태를 확인해 주세요.");

    private final int status;
    private final String defaultMessage;

    ErrorCode(int status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public int status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
