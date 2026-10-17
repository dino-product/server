package com.orbit.schedule.application.port.in.query.dto;

/**
 * 업로드를 마친 사진 한 장(완료보고 사진·현장 사진).
 *
 * @param photoId 업로드한 파일의 식별자
 * @param url 내려받을 주소
 */
public record PhotoInfo(String photoId, String url) {}
