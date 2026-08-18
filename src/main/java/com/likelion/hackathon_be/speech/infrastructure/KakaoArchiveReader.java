package com.likelion.hackathon_be.speech.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class KakaoArchiveReader {
    private static final long MAX_COMPRESSED_BYTES = 20L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 100L * 1024 * 1024;
    private static final int MAX_ENTRIES = 2_000;
    private static final Set<String> ZIP_TYPES = Set.of(
            "application/zip", "application/x-zip-compressed", "application/octet-stream"
    );

    public String readSingleText(MultipartFile file) {
        validateUpload(file);
        List<byte[]> textEntries = new ArrayList<>();
        long expanded = 0;
        int entryCount = 0;
        try (InputStream raw = file.getInputStream();
             ZipArchiveInputStream zip = new ZipArchiveInputStream(raw)) {
            ZipArchiveEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextZipEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new BusinessException(ErrorCode.ZIP_TOO_LARGE);
                }
                validateEntry(entry);
                if (!zip.canReadEntryData(entry)) {
                    throw new BusinessException(ErrorCode.UNSUPPORTED_ARCHIVE);
                }
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream output = isEligibleText(entry.getName()) ? new ByteArrayOutputStream() : null;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > MAX_EXPANDED_BYTES) {
                        throw new BusinessException(ErrorCode.ZIP_TOO_LARGE);
                    }
                    if (output != null) {
                        output.write(buffer, 0, read);
                    }
                }
                if (output != null) {
                    textEntries.add(output.toByteArray());
                }
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_ARCHIVE);
        }
        if (textEntries.size() != 1) {
            throw new BusinessException(ErrorCode.CHAT_TEXT_NOT_FOUND);
        }
        return decode(textEntries.get(0));
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
        if (file.getSize() > MAX_COMPRESSED_BYTES) {
            throw new BusinessException(ErrorCode.ZIP_TOO_LARGE);
        }
        String name = file.getOriginalFilename();
        String type = file.getContentType();
        boolean extensionLooksRight = name != null && name.toLowerCase(Locale.ROOT).endsWith(".zip");
        boolean typeLooksRight = type != null && ZIP_TYPES.contains(type.toLowerCase(Locale.ROOT));
        if (!extensionLooksRight && !typeLooksRight) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
        try {
            byte[] header = file.getInputStream().readNBytes(4);
            boolean localHeader = header.length == 4
                    && header[0] == 'P' && header[1] == 'K'
                    && ((header[2] == 3 && header[3] == 4)
                    || (header[2] == 5 && header[3] == 6)
                    || (header[2] == 7 && header[3] == 8));
            if (!localHeader) {
                throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
    }

    private void validateEntry(ZipArchiveEntry entry) {
        String name = entry.getName().replace('\\', '/');
        Path normalized;
        try {
            normalized = Path.of(name).normalize();
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_ARCHIVE);
        }
        if (name.startsWith("/") || name.matches("^[A-Za-z]:/.*")
                || normalized.isAbsolute() || normalized.startsWith("..")) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_ARCHIVE);
        }
        if (entry.isUnixSymlink()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_ARCHIVE);
        }
        if (entry.getGeneralPurposeBit() != null && entry.getGeneralPurposeBit().usesEncryption()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_ARCHIVE);
        }
    }

    private boolean isEligibleText(String rawName) {
        String name = rawName.replace('\\', '/');
        String[] segments = name.split("/");
        for (String segment : segments) {
            if (segment.equals("__MACOSX") || segment.startsWith(".")) {
                return false;
            }
        }
        return name.toLowerCase(Locale.ROOT).endsWith(".txt");
    }

    private String decode(byte[] bytes) {
        byte[] withoutBom = bytes.length >= 3
                && (bytes[0] & 0xff) == 0xef
                && (bytes[1] & 0xff) == 0xbb
                && (bytes[2] & 0xff) == 0xbf
                ? java.util.Arrays.copyOfRange(bytes, 3, bytes.length)
                : bytes;
        try {
            return decodeStrict(withoutBom, StandardCharsets.UTF_8);
        } catch (CharacterCodingException ignored) {
            try {
                return decodeStrict(withoutBom, Charset.forName("MS949"));
            } catch (CharacterCodingException exception) {
                throw new BusinessException(ErrorCode.CHAT_FORMAT_UNSUPPORTED);
            }
        }
    }

    private String decodeStrict(byte[] bytes, Charset charset) throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString();
    }
}
