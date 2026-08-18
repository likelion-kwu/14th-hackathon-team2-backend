package com.likelion.hackathon_be.speech.infrastructure;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class KakaoChatParser {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final Pattern ANDROID = Pattern.compile(
            "^(\\d{4})년 (\\d{1,2})월 (\\d{1,2})일 (오전|오후) (\\d{1,2}):(\\d{2}), (.+?) : (.*)$"
    );
    private static final Pattern BRACKET = Pattern.compile(
            "^\\[(.+?)] \\[(오전|오후) (\\d{1,2}):(\\d{2})] ?(.*)$"
    );
    private static final Pattern ISO = Pattern.compile(
            "^(\\d{4})[-.] ?(\\d{1,2})[-.] ?(\\d{1,2})[. ]+"
                    + "(\\d{1,2}):(\\d{2})(?::\\d{2})?, (.+?) : (.*)$"
    );
    private static final Pattern PC_CSV = Pattern.compile(
            "^(\\d{4})-(\\d{2})-(\\d{2}) (\\d{2}):(\\d{2})(?::\\d{2})?,"
                    + "(?:\\\"([^\\\"]+)\\\"|([^,]+)),(?:\\\"(.*)\\\"|(.*))$"
    );
    private static final Pattern DOTTED_KOREAN = Pattern.compile(
            "^(\\d{4})\\. ?(\\d{1,2})\\. ?(\\d{1,2})\\. (오전|오후) "
                    + "(\\d{1,2}):(\\d{2}), (.+?) : (.*)$"
    );
    private static final Pattern DATE_LINE = Pattern.compile(
            ".*?(\\d{4})년 (\\d{1,2})월 (\\d{1,2})일.*"
    );
    private static final Set<String> EXACT_SYSTEM_MESSAGES = Set.of(
            "사진을 보냈습니다.", "동영상을 보냈습니다.", "파일을 보냈습니다.",
            "삭제된 메시지입니다.", "이모티콘", "송금했습니다."
    );

    public KakaoChatData parse(String text) {
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        LocalDate currentDate = LocalDate.of(2000, 1, 1);
        List<MutableMessage> parsed = new ArrayList<>();
        long sequence = 0;
        for (String line : lines) {
            Matcher android = ANDROID.matcher(line);
            if (android.matches()) {
                parsed.add(message(
                        date(android, 1),
                        to24Hour(android.group(4), integer(android, 5)),
                        integer(android, 6),
                        android.group(7),
                        android.group(8),
                        sequence++
                ));
                continue;
            }
            Matcher bracket = BRACKET.matcher(line);
            if (bracket.matches()) {
                parsed.add(message(
                        currentDate,
                        to24Hour(bracket.group(2), integer(bracket, 3)),
                        integer(bracket, 4),
                        bracket.group(1),
                        bracket.group(5),
                        sequence++
                ));
                continue;
            }
            Matcher iso = ISO.matcher(line);
            if (iso.matches()) {
                parsed.add(message(
                        date(iso, 1),
                        integer(iso, 4),
                        integer(iso, 5),
                        iso.group(6),
                        iso.group(7),
                        sequence++
                ));
                continue;
            }
            Matcher csv = PC_CSV.matcher(line);
            if (csv.matches()) {
                String sender = csv.group(6) == null ? csv.group(7) : csv.group(6);
                String content = csv.group(8) == null ? csv.group(9) : csv.group(8).replace("\"\"", "\"");
                parsed.add(message(
                        date(csv, 1),
                        integer(csv, 4),
                        integer(csv, 5),
                        sender,
                        content,
                        sequence++
                ));
                continue;
            }
            Matcher dotted = DOTTED_KOREAN.matcher(line);
            if (dotted.matches()) {
                parsed.add(message(
                        date(dotted, 1),
                        to24Hour(dotted.group(4), integer(dotted, 5)),
                        integer(dotted, 6),
                        dotted.group(7),
                        dotted.group(8),
                        sequence++
                ));
                continue;
            }
            Matcher date = DATE_LINE.matcher(line);
            if (date.matches()) {
                currentDate = date(date, 1);
                continue;
            }
            if (!line.isBlank() && !parsed.isEmpty() && !isHeader(line)) {
                parsed.get(parsed.size() - 1).append(line);
            }
        }

        List<KakaoMessage> messages = parsed.stream()
                .filter(message -> !isSystemMessage(message.content.toString()))
                .filter(message -> !message.sender.isBlank() && !message.content.toString().isBlank())
                .map(message -> new KakaoMessage(
                        message.sentAt.toEpochMilli(),
                        message.sequence,
                        message.sender,
                        message.content.toString().trim()
                ))
                .toList();
        if (messages.isEmpty()) {
            throw new BusinessException(ErrorCode.CHAT_FORMAT_UNSUPPORTED);
        }

        Map<String, String> participantIds = new LinkedHashMap<>();
        for (KakaoMessage message : messages) {
            participantIds.computeIfAbsent(message.sender(), ignored -> "p" + (participantIds.size() + 1));
        }
        List<KakaoParticipant> participants = participantIds.entrySet().stream()
                .map(entry -> new KakaoParticipant(entry.getValue(), entry.getKey()))
                .toList();
        return new KakaoChatData(participants, messages);
    }

    private MutableMessage message(
            LocalDate date,
            int hour,
            int minute,
            String sender,
            String content,
            long sequence
    ) {
        try {
            Instant sentAt = LocalDateTime.of(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), hour, minute)
                    .atZone(KOREA)
                    .toInstant();
            return new MutableMessage(sentAt, sequence, sender.trim(), new StringBuilder(content));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.CHAT_FORMAT_UNSUPPORTED);
        }
    }

    private LocalDate date(Matcher matcher, int firstGroup) {
        return LocalDate.of(
                Integer.parseInt(matcher.group(firstGroup)),
                Integer.parseInt(matcher.group(firstGroup + 1)),
                Integer.parseInt(matcher.group(firstGroup + 2))
        );
    }

    private int integer(Matcher matcher, int group) {
        return Integer.parseInt(matcher.group(group));
    }

    private int to24Hour(String period, int hour) {
        int normalized = hour % 12;
        return "오후".equals(period) ? normalized + 12 : normalized;
    }

    private boolean isSystemMessage(String content) {
        String trimmed = content.trim();
        return EXACT_SYSTEM_MESSAGES.contains(trimmed)
                || trimmed.endsWith("님이 들어왔습니다.")
                || trimmed.endsWith("님이 나갔습니다.")
                || trimmed.contains("송금봉투를 보냈습니다")
                || trimmed.startsWith("사진 ") && trimmed.endsWith("장을 보냈습니다.");
    }

    private boolean isHeader(String line) {
        return line.startsWith("Talk_")
                || line.startsWith("저장한 날짜")
                || line.contains("카카오톡 대화")
                || line.matches("^-{3,}.*-{3,}$");
    }

    private static final class MutableMessage {
        private final Instant sentAt;
        private final long sequence;
        private final String sender;
        private final StringBuilder content;

        private MutableMessage(Instant sentAt, long sequence, String sender, StringBuilder content) {
            this.sentAt = sentAt;
            this.sequence = sequence;
            this.sender = sender;
            this.content = content;
        }

        private void append(String line) {
            content.append('\n').append(line);
        }
    }
}
