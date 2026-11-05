package com.orbit.media.domain;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** 시그니처(파일 앞 바이트)로 알아볼 수 있는 이미지 형식과 그 Content-Type. */
public enum ImageFormat {
    JPEG(Set.of("image/jpeg")),
    PNG(Set.of("image/png")),
    WEBP(Set.of("image/webp")),
    /** HEIC·HEIF(ISO BMFF {@code ftyp} 상자의 HEIF 계열 브랜드). */
    HEIF(Set.of("image/heic", "image/heif"));

    /** 판별에 필요한 앞부분 길이. */
    public static final int SIGNATURE_LENGTH = 16;

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final Set<String> HEIF_BRANDS =
            Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1");

    private final Set<String> contentTypes;

    ImageFormat(Set<String> contentTypes) {
        this.contentTypes = contentTypes;
    }

    /** Content-Type이 가리키는 형식. 매개변수(예: {@code ; charset})와 대소문자는 무시한다. */
    public static Optional<ImageFormat> ofContentType(String contentType) {
        if (contentType == null) {
            return Optional.empty();
        }
        String type = normalize(contentType);
        return Arrays.stream(values())
                .filter(format -> format.contentTypes.contains(type))
                .findFirst();
    }

    /** 파일 앞부분으로 형식을 판별한다. 알 수 없으면 비어 있다. */
    public static Optional<ImageFormat> detect(byte[] head) {
        if (head == null) {
            return Optional.empty();
        }
        if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (head.length >= PNG_SIGNATURE.length
                && Arrays.equals(Arrays.copyOf(head, PNG_SIGNATURE.length), PNG_SIGNATURE)) {
            return Optional.of(PNG);
        }
        if (head.length >= 12
                && ascii(head, 0, 4).equals("RIFF")
                && ascii(head, 8, 4).equals("WEBP")) {
            return Optional.of(WEBP);
        }
        if (head.length >= 12 && ascii(head, 4, 4).equals("ftyp") && HEIF_BRANDS.contains(ascii(head, 8, 4))) {
            return Optional.of(HEIF);
        }
        return Optional.empty();
    }

    /** 대소문자·매개변수를 뺀 Content-Type. */
    public static String normalize(String contentType) {
        int parameters = contentType.indexOf(';');
        String type = parameters < 0 ? contentType : contentType.substring(0, parameters);
        return type.trim().toLowerCase(Locale.ROOT);
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }
}
