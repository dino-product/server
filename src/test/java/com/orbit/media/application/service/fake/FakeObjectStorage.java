package com.orbit.media.application.service.fake;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.orbit.media.application.port.out.ObjectStoragePort;
import com.orbit.media.application.port.out.ObjectStorageUnavailableException;
import com.orbit.media.application.port.out.PresignedUpload;
import com.orbit.media.application.port.out.StoredObject;

/**
 * 메모리 객체 저장소. 테스트가 {@link #put}으로 업로드를 흉내 내고, {@link #failing}으로 저장소 호출 장애를, {@link #signingFails}로 서명 실패(자격 증명을
 * 얻지 못함)를 낸다. 객체는 올릴 때마다 새 ETag를 받고, S3처럼 앞부분 읽기·복사는 확인한 ETag와 다르거나(412) 객체가 없으면(404) 하지 않는다. 확인과 읽기·복사
 * 사이에 다시 올리거나 지우는 경합은 {@link #afterCall}로 만든다. 호출한 연산을 기록한다.
 */
public class FakeObjectStorage implements ObjectStoragePort {

    public static final Instant EXPIRES_AT = Instant.parse("2026-10-21T01:10:00Z");

    private record Stored(byte[] bytes, String contentType, String eTag) {}

    private final Map<String, Stored> objects = new HashMap<>();
    private final Set<String> expired = new LinkedHashSet<>();
    private final List<String> calls = new ArrayList<>();
    private final Map<String, Runnable> afterCalls = new HashMap<>();
    private int uploads;
    private boolean failing;
    private boolean signingFails;

    public void put(String key, String contentType, byte[] bytes) {
        uploads++;
        objects.put(key, new Stored(bytes.clone(), contentType, "\"etag-" + uploads + "\""));
    }

    public void remove(String key) {
        objects.remove(key);
    }

    /** 기록 이름이 {@code call}인 호출(예: {@code find incoming/x})이 끝난 직후 한 번 {@code action}을 실행한다. */
    public void afterCall(String call, Runnable action) {
        afterCalls.put(call, action);
    }

    public void failing(boolean failing) {
        this.failing = failing;
    }

    public void signingFails(boolean signingFails) {
        this.signingFails = signingFails;
    }

    public boolean has(String key) {
        return objects.containsKey(key);
    }

    public Set<String> expired() {
        return Set.copyOf(expired);
    }

    public List<String> calls() {
        return List.copyOf(calls);
    }

    @Override
    public PresignedUpload presignUpload(String key, String contentType, long contentLength) {
        sign("presignUpload " + key);
        return new PresignedUpload(
                "https://storage.test/" + key,
                Map.of("content-type", contentType, "content-length", String.valueOf(contentLength)),
                EXPIRES_AT);
    }

    @Override
    public Optional<StoredObject> find(String key) {
        String call = "find " + key;
        call(call);
        Optional<StoredObject> found = Optional.ofNullable(objects.get(key))
                .map(stored -> new StoredObject(stored.bytes().length, stored.contentType(), stored.eTag()));
        after(call);
        return found;
    }

    @Override
    public Optional<byte[]> readFirstBytes(String key, int length, String eTag) {
        String call = "read " + key;
        call(call);
        Optional<byte[]> read = current(key, eTag)
                .map(stored -> Arrays.copyOf(stored.bytes(), Math.min(length, stored.bytes().length)));
        after(call);
        return read;
    }

    @Override
    public boolean copy(String sourceKey, String targetKey, String eTag) {
        String call = "copy " + sourceKey + " " + targetKey;
        call(call);
        Optional<Stored> source = current(sourceKey, eTag);
        source.ifPresent(stored -> objects.put(targetKey, stored));
        after(call);
        return source.isPresent();
    }

    @Override
    public void markExpired(String key) {
        call("markExpired " + key);
        expired.add(key);
    }

    @Override
    public String presignDownload(String key) {
        sign("presignDownload " + key);
        return "https://storage.test/get/" + key;
    }

    /** 객체가 있고 ETag가 같을 때만(S3의 404·412 흉내). */
    private Optional<Stored> current(String key, String eTag) {
        return Optional.ofNullable(objects.get(key)).filter(stored -> Objects.equals(stored.eTag(), eTag));
    }

    private void after(String call) {
        Runnable action = afterCalls.remove(call);
        if (action != null) {
            action.run();
        }
    }

    private void sign(String call) {
        calls.add(call);
        if (signingFails) {
            throw new ObjectStorageUnavailableException(new RuntimeException("no credentials"));
        }
    }

    private void call(String call) {
        calls.add(call);
        if (failing) {
            throw new ObjectStorageUnavailableException(new RuntimeException("down"));
        }
    }
}
