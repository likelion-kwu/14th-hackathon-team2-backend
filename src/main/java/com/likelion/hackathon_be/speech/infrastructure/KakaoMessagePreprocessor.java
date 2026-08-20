package com.likelion.hackathon_be.speech.infrastructure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class KakaoMessagePreprocessor {
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:01[016789])[- ]?\\d{3,4}[- ]?\\d{4}(?!\\d)");
    private static final Pattern ACCOUNT = Pattern.compile("(?<!\\d)(?:\\d[- ]?){9,15}\\d(?!\\d)");
    private static final Set<String> SHORT_UTTERANCES = Set.of(
            "ㅇㅇ", "ㄴㄴ", "왜", "응", "아", "어", "네", "넵", "ㅋㅋ", "ㅎㅎ", "ㅋ", "ㅎ"
    );
    private static final Set<String> PROFANITY = Set.of("씨발", "시발", "ㅅㅂ", "병신", "개새끼", "좆");

    public PreprocessedSpeechData preprocess(KakaoChatData data, String participantId) {
        KakaoParticipant selected = data.participants().stream()
                .filter(participant -> participant.id().equals(participantId))
                .findFirst()
                .orElse(null);
        if (selected == null) {
            return null;
        }
        List<KakaoMessage> timeline = data.messages().stream()
                .sorted(Comparator.comparingLong(KakaoMessage::sentAtEpochMilli)
                        .thenComparingLong(KakaoMessage::sequence))
                .toList();
        Map<String, Integer> repetitions = new HashMap<>();
        Map<String, String> repetitionSamples = new HashMap<>();
        Map<String, Integer> observedProfanityCounts = new HashMap<>();
        List<String> participantNames = data.participants().stream().map(KakaoParticipant::displayName).toList();
        timeline.stream()
                .filter(message -> message.sender().equals(selected.displayName()))
                .forEach(message -> {
                    String masked = mask(message.content(), participantNames, selected.displayName());
                    String normalized = normalize(masked);
                    if (!normalized.isBlank()) {
                        repetitions.merge(normalized, 1, Integer::sum);
                        repetitionSamples.putIfAbsent(normalized, masked);
                    }
                    countProfanity(message.content(), observedProfanityCounts);
                });

        List<GroupedMessage> grouped = splitLongMessages(groupSelected(timeline, selected.displayName()));
        List<GroupedMessage> valid = new ArrayList<>();
        for (GroupedMessage message : grouped) {
            if (message.substantive()) {
                valid.add(message);
            }
        }

        int validCount = valid.size();
        List<GroupedMessage> latest = valid.stream()
                .sorted(Comparator.comparingLong(GroupedMessage::sentAtEpochMilli).reversed())
                .limit(500)
                .sorted(Comparator.comparingLong(GroupedMessage::sentAtEpochMilli))
                .toList();
        List<PreprocessedSpeechMessage> masked = new ArrayList<>();
        int index = 1;
        for (GroupedMessage message : latest) {
            masked.add(new PreprocessedSpeechMessage(
                    "m-%03d".formatted(index++),
                    mask(message.context(), participantNames, selected.displayName()),
                    mask(message.content(), participantNames, selected.displayName())
            ));
        }
        Map<String, Integer> frequent = new LinkedHashMap<>();
        repetitions.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(30)
                .forEach(entry -> {
                    String maskedKey = repetitionSamples.getOrDefault(entry.getKey(), entry.getKey());
                    if (maskedKey.length() <= 50) {
                        frequent.merge(maskedKey, entry.getValue(), Integer::sum);
                    }
                });
        return new PreprocessedSpeechData(masked, validCount, frequent, observedProfanityCounts);
    }

    private List<GroupedMessage> splitLongMessages(List<GroupedMessage> grouped) {
        List<GroupedMessage> result = new ArrayList<>();
        for (GroupedMessage message : grouped) {
            if (message.content().length() <= 250) {
                result.add(message);
                continue;
            }
            String[] sentences = message.content().split("(?<=[.!?])|\\R");
            for (String sentence : sentences) {
                String trimmed = sentence.trim();
                for (int start = 0; start < trimmed.length(); start += 250) {
                    result.add(new GroupedMessage(
                            message.sentAtEpochMilli(),
                            message.context(),
                            trimmed.substring(start, Math.min(trimmed.length(), start + 250)),
                            !isShort(normalize(trimmed.substring(start, Math.min(trimmed.length(), start + 250))))
                    ));
                }
            }
        }
        return result;
    }

    private List<GroupedMessage> groupSelected(List<KakaoMessage> timeline, String selectedName) {
        List<GroupedMessage> groups = new ArrayList<>();
        String previousOther = "";
        GroupBuilder current = null;
        for (KakaoMessage message : timeline) {
            if (!message.sender().equals(selectedName)) {
                if (current != null) {
                    groups.add(current.build());
                    current = null;
                }
                previousOther = message.content();
                continue;
            }
            boolean canAppend = current != null
                    && current.count < 5
                    && Duration.ofMillis(message.sentAtEpochMilli() - current.lastSentAt).compareTo(Duration.ofMinutes(1)) <= 0;
            if (canAppend) {
                current.append(message);
            } else {
                if (current != null) {
                    groups.add(current.build());
                }
                current = new GroupBuilder(message, previousOther, !isShort(normalize(message.content())));
            }
        }
        if (current != null) {
            groups.add(current.build());
        }
        return groups;
    }

    private boolean isShort(String normalized) {
        return normalized.isBlank()
                || normalized.length() <= 1
                || SHORT_UTTERANCES.contains(normalized)
                || normalized.matches("[ㅋㅎㅠㅜ]+")
                || normalized.matches("[.!?]+");
    }

    private String mask(String content, List<String> participants, String selectedName) {
        String masked = content == null ? "" : content;
        String urlToken = "\uE000\uE001";
        String emailToken = "\uE002\uE003";
        String phoneToken = "\uE004\uE005";
        String accountToken = "\uE006\uE007";
        masked = URL.matcher(masked).replaceAll(urlToken);
        masked = EMAIL.matcher(masked).replaceAll(emailToken);
        masked = PHONE.matcher(masked).replaceAll(phoneToken);
        masked = ACCOUNT.matcher(masked).replaceAll(accountToken);
        List<String> longestFirst = participants.stream()
                .filter(name -> name != null && !name.isBlank())
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        Map<String, String> participantTokens = new LinkedHashMap<>();
        int participantIndex = 0;
        for (String participant : longestFirst) {
            String token = "\uE100" + participantIndex++ + "\uE101";
            participantTokens.put(token, participant.equals(selectedName) ? "[USER]" : "[PERSON]");
            masked = masked.replace(participant, token);
        }
        masked = masked.replace(urlToken, "[URL]")
                .replace(emailToken, "[EMAIL]")
                .replace(phoneToken, "[PHONE]")
                .replace(accountToken, "[ACCOUNT]");
        for (Map.Entry<String, String> token : participantTokens.entrySet()) {
            masked = masked.replace(token.getKey(), token.getValue());
        }
        return masked.length() > 500 ? masked.substring(0, 500) : masked;
    }

    private String normalize(String content) {
        return content.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private void countProfanity(String content, Map<String, Integer> counts) {
        String searchable = content.replace("시발점", "");
        for (String profanity : PROFANITY) {
            int from = 0;
            while ((from = searchable.indexOf(profanity, from)) >= 0) {
                counts.merge(profanity, 1, Integer::sum);
                from += profanity.length();
            }
        }
    }

    private record GroupedMessage(long sentAtEpochMilli, String context, String content, boolean substantive) {
    }

    private static final class GroupBuilder {
        private final long firstSentAt;
        private long lastSentAt;
        private int count = 1;
        private final String context;
        private final StringBuilder content;
        private boolean substantive;

        private GroupBuilder(KakaoMessage message, String context, boolean substantive) {
            this.firstSentAt = message.sentAtEpochMilli();
            this.lastSentAt = message.sentAtEpochMilli();
            this.context = context;
            this.content = new StringBuilder(message.content());
            this.substantive = substantive;
        }

        private void append(KakaoMessage message) {
            content.append(" / ").append(message.content());
            lastSentAt = message.sentAtEpochMilli();
            count++;
            substantive = substantive || !isShortValue(message.content());
        }

        private GroupedMessage build() {
            return new GroupedMessage(firstSentAt, context, content.toString(), substantive);
        }

        private boolean isShortValue(String value) {
            String normalized = value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
            return normalized.isBlank()
                    || normalized.length() <= 1
                    || SHORT_UTTERANCES.contains(normalized)
                    || normalized.matches("[ㅋㅎㅠㅜ]+")
                    || normalized.matches("[.!?]+");
        }
    }
}
