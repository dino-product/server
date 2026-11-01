package com.orbit.notification.domain;

/** 앱 푸시 기기의 운영체제. 발신은 플랫폼과 무관하게 같은 제공자로 보내며, 어느 기기인지 구분해 볼 때 쓴다. */
public enum PushPlatform {
    IOS,
    ANDROID
}
