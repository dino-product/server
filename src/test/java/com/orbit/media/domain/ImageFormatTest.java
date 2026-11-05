package com.orbit.media.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("이미지 형식 판별")
class ImageFormatTest {

    @Test
    @DisplayName("JPEG·PNG·WebP·HEIC/HEIF 시그니처를 알아본다")
    void detectsSignatures() {
        assertThat(ImageFormat.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0))).contains(ImageFormat.JPEG);
        assertThat(ImageFormat.detect(bytes(0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0)))
                .contains(ImageFormat.PNG);
        assertThat(ImageFormat.detect(ascii("RIFF\0\0\0\0WEBPVP8 "))).contains(ImageFormat.WEBP);
        assertThat(ImageFormat.detect(ascii("\0\0\0\u0018ftypheic\0\0\0\0"))).contains(ImageFormat.HEIF);
        assertThat(ImageFormat.detect(ascii("\0\0\0\u0018ftypmif1\0\0\0\0"))).contains(ImageFormat.HEIF);
    }

    @Test
    @DisplayName("이미지가 아니거나 너무 짧으면 알 수 없다")
    void rejectsOthers() {
        assertThat(ImageFormat.detect(ascii("%PDF-1.7\n"))).isEmpty();
        assertThat(ImageFormat.detect(ascii("GIF89a"))).isEmpty();
        assertThat(ImageFormat.detect(ascii("\0\0\0\u0018ftypmp42\0\0\0\0"))).isEmpty();
        assertThat(ImageFormat.detect(bytes(0xFF, 0xD8))).isEmpty();
        assertThat(ImageFormat.detect(new byte[0])).isEmpty();
        assertThat(ImageFormat.detect(null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "IMAGE/JPEG", "image/jpeg; charset=binary", " image/jpeg "})
    @DisplayName("Content-Type은 대소문자·매개변수를 무시하고 형식에 대응한다")
    void mapsContentType(String contentType) {
        assertThat(ImageFormat.ofContentType(contentType)).contains(ImageFormat.JPEG);
    }

    @Test
    @DisplayName("HEIC와 HEIF는 같은 형식이고 이미지가 아닌 형식은 대응하지 않는다")
    void mapsHeifAndUnknown() {
        assertThat(ImageFormat.ofContentType("image/heic")).contains(ImageFormat.HEIF);
        assertThat(ImageFormat.ofContentType("image/heif")).contains(ImageFormat.HEIF);
        assertThat(ImageFormat.ofContentType("image/gif")).isEmpty();
        assertThat(ImageFormat.ofContentType("application/pdf")).isEmpty();
        assertThat(ImageFormat.ofContentType(null)).isEqualTo(Optional.empty());
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }
}
