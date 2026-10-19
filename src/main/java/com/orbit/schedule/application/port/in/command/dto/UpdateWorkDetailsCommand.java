package com.orbit.schedule.application.port.in.command.dto;

import java.util.List;

import com.orbit.schedule.domain.PaymentMethod;

/**
 * 작업 기본정보 수정 요청. 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별한다. 작업명·작업 유형·고객정보·결제정보·현장 사진을 한꺼번에
 * 교체하므로(부분 수정 아님) 바꾸지 않을 항목도 현재 값을 담아야 하며, null인 선택 항목은 비운다.
 *
 * @param customerAddress 현장 기본주소
 * @param fee 원 단위 요금
 * @param sitePhotoIds 업로드를 마친 현장 사진 식별자(붙인 순서). null이면 모두 지운다
 */
public record UpdateWorkDetailsCommand(
        Long accountId,
        Long organizationId,
        Long workId,
        String name,
        Long workTypeId,
        String customerName,
        String customerPhone,
        String customerPostalCode,
        String customerAddress,
        String customerAddressDetail,
        Long fee,
        PaymentMethod paymentMethod,
        List<String> sitePhotoIds) {}
