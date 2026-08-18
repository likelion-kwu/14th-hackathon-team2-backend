package com.likelion.hackathon_be.speech.infrastructure;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KakaoInputPipelineTests {
    private final KakaoChatParser parser = new KakaoChatParser();

    @Test
    void parsesAndroidAndBracketKoreanExports() {
        KakaoChatData android = parser.parse("""
                저장한 날짜 : 2026년 8월 19일
                2026년 8월 19일 오전 9:29, 지섭 : 안녕 반가워
                2026년 8월 19일 오전 9:30, 현 : 오늘 운동했어?
                """);
        KakaoChatData bracket = parser.parse("""
                --------------- 2026년 8월 19일 수요일 ---------------
                [지섭] [오전 9:29] 안녕 반가워
                [현] [오전 9:30] 오늘 운동했어?
                """);
        KakaoChatData pc = parser.parse("""
                2026-08-19 09:29:00,"지섭","안녕 반가워"
                2026-08-19 09:30:00,"현","오늘 운동했어?"
                """);

        assertThat(android.participants()).extracting(KakaoParticipant::id).containsExactly("p1", "p2");
        assertThat(bracket.messages()).hasSize(2);
        assertThat(bracket.messages().get(1).sender()).isEqualTo("현");
        assertThat(pc.participants()).extracting(KakaoParticipant::displayName).containsExactly("지섭", "현");
    }

    @Test
    void parsesAnonymizedAndroidIosAndPcExportFixtures() throws Exception {
        for (String fixture : List.of("android-ko.txt", "ios-ko.txt", "pc-ko.csv.txt")) {
            String text;
            try (var input = new ClassPathResource("kakao/" + fixture).getInputStream()) {
                text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            KakaoChatData parsed = parser.parse(text);
            assertThat(parsed.participants()).extracting(KakaoParticipant::displayName)
                    .containsExactly("참여자A", "참여자B");
            assertThat(parsed.messages()).hasSize(2);
        }
    }

    @Test
    void masksPiiAndKeepsExactlyFiftyValidGroupedMessages() {
        List<KakaoMessage> messages = new ArrayList<>();
        long time = 1_700_000_000_000L;
        long sequence = 0;
        for (int index = 0; index < 50; index++) {
            messages.add(new KakaoMessage(time++, sequence++, "상대", "지섭아 연락처 알려줘"));
            messages.add(new KakaoMessage(
                    time++,
                    sequence++,
                    "지섭",
                    "메일 user@example.com 전화 010-1234-5678 링크 https://example.com 답장 " + index
            ));
        }
        KakaoChatData data = new KakaoChatData(
                List.of(new KakaoParticipant("p1", "상대"), new KakaoParticipant("p2", "지섭")),
                messages
        );

        PreprocessedSpeechData result = new KakaoMessagePreprocessor().preprocess(data, "p2");

        assertThat(result.validMessageCount()).isEqualTo(50);
        assertThat(result.messages()).hasSize(50);
        assertThat(result.messages().get(0).context()).contains("[USER]").doesNotContain("지섭");
        assertThat(result.messages().get(0).userMessage())
                .contains("[EMAIL]", "[PHONE]", "[URL]")
                .doesNotContain("user@example.com", "010-1234-5678", "example.com");
    }

    @Test
    void preservesFortyNineBoundaryAndLimitsAiInputToLatestFiveHundred() {
        KakaoMessagePreprocessor preprocessor = new KakaoMessagePreprocessor();

        PreprocessedSpeechData belowMinimum = preprocessor.preprocess(chatWithAlternatingMessages(49), "p2");
        PreprocessedSpeechData aboveMaximum = preprocessor.preprocess(chatWithAlternatingMessages(501), "p2");

        assertThat(belowMinimum.validMessageCount()).isEqualTo(49);
        assertThat(aboveMaximum.validMessageCount()).isEqualTo(501);
        assertThat(aboveMaximum.messages()).hasSize(500);
        assertThat(aboveMaximum.messages().get(0).userMessage()).contains("문장 1");
        assertThat(aboveMaximum.messages().get(499).userMessage()).contains("문장 500");
    }

    @Test
    void excludesGroupedShortRepliesButPreservesTheirIndividualRepetitionCount() {
        List<KakaoMessage> messages = new ArrayList<>();
        long time = 1_700_000_000_000L;
        for (int index = 0; index < 5; index++) {
            messages.add(new KakaoMessage(time + index * 1_000L, index, "사용자", "ㅋㅋ"));
        }
        KakaoChatData data = new KakaoChatData(
                List.of(new KakaoParticipant("p1", "사용자")),
                messages
        );

        PreprocessedSpeechData result = new KakaoMessagePreprocessor().preprocess(data, "p1");

        assertThat(result.validMessageCount()).isZero();
        assertThat(result.messages()).isEmpty();
        assertThat(result.repetitionCounts()).containsEntry("ㅋㅋ", 5);
    }

    @Test
    void masksParticipantBeforeNormalizingRepetitionStatisticsAndCountsProfanity() {
        KakaoChatData data = new KakaoChatData(
                List.of(new KakaoParticipant("p1", "김 철수"), new KakaoParticipant("p2", "상대")),
                List.of(
                        new KakaoMessage(1_000, 1, "김 철수", "김 철수 오늘도 해보자"),
                        new KakaoMessage(70_000, 2, "상대", "왜?"),
                        new KakaoMessage(140_000, 3, "김 철수", "시발 그래도 충분히 해볼 만해"),
                        new KakaoMessage(210_000, 4, "상대", "시발점이 뭐야?"),
                        new KakaoMessage(280_000, 5, "김 철수", "시발점부터 다시 설명할게")
                )
        );

        PreprocessedSpeechData result = new KakaoMessagePreprocessor().preprocess(data, "p1");

        assertThat(result.repetitionCounts().keySet())
                .allSatisfy(value -> assertThat(value).doesNotContain("김 철수", "김철수"));
        assertThat(result.messages()).allSatisfy(message ->
                assertThat(message.userMessage()).doesNotContain("김 철수"));
        assertThat(result.observedProfanityCounts()).containsEntry("시발", 1);
    }

    @Test
    void rejectsZipSlipAndMultipleVisibleTextFiles() throws Exception {
        KakaoArchiveReader reader = new KakaoArchiveReader();
        MockMultipartFile traversal = zip(Map.of("../chat.txt", "unsafe"));
        MockMultipartFile multiple = zip(Map.of("one.txt", "a", "two.txt", "b"));

        assertThatThrownBy(() -> reader.readSingleText(traversal))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_ARCHIVE));
        assertThatThrownBy(() -> reader.readSingleText(multiple))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHAT_TEXT_NOT_FOUND));
    }

    @Test
    void ignoresHiddenMetadataAndRejectsEncryptedOrCorruptArchives() throws Exception {
        KakaoArchiveReader reader = new KakaoArchiveReader();
        MockMultipartFile withMetadata = zip(Map.of(
                "__MACOSX/._chat.txt", "metadata",
                ".hidden.txt", "hidden",
                "chat.txt", "정상 대화"
        ));
        byte[] encryptedBytes = zip(Map.of("chat.txt", "secret")).getBytes();
        markEncrypted(encryptedBytes);
        MockMultipartFile encrypted = new MockMultipartFile(
                "file", "encrypted.zip", "application/zip", encryptedBytes
        );
        MockMultipartFile corrupt = new MockMultipartFile(
                "file", "broken.zip", "application/zip", new byte[]{'P', 'K', 3, 4, 1, 2, 3}
        );

        assertThat(reader.readSingleText(withMetadata)).isEqualTo("정상 대화");
        assertThatThrownBy(() -> reader.readSingleText(encrypted))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_ARCHIVE));
        assertThatThrownBy(() -> reader.readSingleText(corrupt))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsInvalidExportDatesAsUnsupportedFormat() {
        assertThatThrownBy(() -> parser.parse(
                "2026년 2월 30일 오전 9:29, 참여자A : 존재하지 않는 날짜"
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHAT_FORMAT_UNSUPPORTED));
    }

    private MockMultipartFile zip(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", "KakaoTalk.zip", "application/zip", output.toByteArray());
    }

    private KakaoChatData chatWithAlternatingMessages(int count) {
        List<KakaoMessage> messages = new ArrayList<>();
        long time = 1_700_000_000_000L;
        long sequence = 0;
        for (int index = 0; index < count; index++) {
            messages.add(new KakaoMessage(time++, sequence++, "상대", "직전 문맥 " + index));
            messages.add(new KakaoMessage(time++, sequence++, "사용자", "분석할 충분한 문장 " + index));
        }
        return new KakaoChatData(
                List.of(new KakaoParticipant("p1", "상대"), new KakaoParticipant("p2", "사용자")),
                messages
        );
    }

    private void markEncrypted(byte[] archive) {
        for (int index = 0; index + 10 < archive.length; index++) {
            boolean local = archive[index] == 'P' && archive[index + 1] == 'K'
                    && archive[index + 2] == 3 && archive[index + 3] == 4;
            boolean central = archive[index] == 'P' && archive[index + 1] == 'K'
                    && archive[index + 2] == 1 && archive[index + 3] == 2;
            if (local) {
                archive[index + 6] = (byte) (archive[index + 6] | 1);
            }
            if (central) {
                archive[index + 8] = (byte) (archive[index + 8] | 1);
            }
        }
    }
}
