package com.orbit.media.application.service.fake;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.orbit.media.application.port.out.ObjectStoragePort;
import com.orbit.media.application.port.out.ObjectStorageUnavailableException;
import com.orbit.media.application.port.out.PresignedUpload;
import com.orbit.media.application.port.out.StoredObject;

/** 메모리 객체 저장소. 테스트가 {@link #put}으로 업로드를 흉내 내고, {@link #failing}으로 장애를 낸다. 호출한 연산을 기록한다. */
public class FakeObjectStorage implements ObjectStoragePort {

    public static final Instant EXPIRES_AT = Instant.parse("2026-10-21T01:10:00Z");

    private record Stored(byte[] bytes, String contentType) {}

    private final Map<String, Stored> objects = new HashMap<>();
    private final Set<String> expired = new LinkedHashSet<>();
    private final List<String> calls = new ArrayList<>();
    private boolean failing;

    public void put(String key, String contentType, byte[] bytes) {
        objects.put(key, new Stored(bytes.clone(), contentType));
    }

    public void failing(boolean failing) {
        this.failing = failing;
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
        calls.add("presignUpload " + key);
        return new PresignedUpload(
                "https://storage.test/" + key,
                Map.of("content-type", contentType, "content-length", String.valueOf(contentLength)),
                EXPIRES_AT);
    }

    @Override
    public Optional<StoredObject> find(String key) {
        call("find " + key);
        return Optional.ofNullable(objects.get(key))
                .map(stored -> new StoredObject(stored.bytes().length, stored.contentType()));
    }

    @Override
    public byte[] readFirstBytes(String key, int length) {
        call("read " + key);
        byte[] bytes = objects.get(key).bytes();
        return Arrays.copyOf(bytes, Math.min(length, bytes.length));
    }

    @Override
    public void copy(String sourceKey, String targetKey) {
        call("copy " + sourceKey + " " + targetKey);
        objects.put(targetKey, objects.get(sourceKey));
    }

    @Override
    public void markExpired(String key) {
        call("markExpired " + key);
        expired.add(key);
    }

    @Override
    public String presignDownload(String key) {
        calls.add("presignDownload " + key);
        return "https://storage.test/get/" + key;
    }

    private void call(String call) {
        calls.add(call);
        if (failing) {
            throw new ObjectStorageUnavailableException(new RuntimeException("down"));
        }
    }
}
