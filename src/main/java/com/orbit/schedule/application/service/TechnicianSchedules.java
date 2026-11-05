package com.orbit.schedule.application.service;

import java.time.Instant;
import java.util.Comparator;

import com.orbit.schedule.application.port.in.query.dto.MyScheduleInfo;
import com.orbit.schedule.domain.SiteAddress;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;

/** 기사 본인 작업 목록(내 일정·응답 필요·지금 수행 가능)이 함께 쓰는 항목 변환과 정렬. */
final class TechnicianSchedules {

    /** 시작시각, 작업 식별자 순. */
    static final Comparator<MyScheduleInfo.ScheduledWork> ORDER = Comparator.comparing(
                    MyScheduleInfo.ScheduledWork::startTime)
            .thenComparing(MyScheduleInfo.ScheduledWork::workId);

    private TechnicianSchedules() {}

    /** 지금 일정이 있는 작업을 기사 목록 항목으로 바꾼다. 지연은 now 기준이다. */
    static MyScheduleInfo.ScheduledWork scheduledWork(Work work, Instant now) {
        WorkSchedule schedule = work.schedule().orElseThrow();
        return new MyScheduleInfo.ScheduledWork(
                work.id().orElseThrow().value(),
                work.name(),
                work.status(),
                work.currentAssignmentNumber(),
                schedule.startTime(),
                schedule.endTime(),
                work.customerInfo().name().orElse(null),
                work.customerInfo().address().flatMap(SiteAddress::baseAddress).orElse(null),
                work.customerInfo()
                        .address()
                        .flatMap(SiteAddress::detailAddress)
                        .orElse(null),
                WorkViews.location(work.customerInfo()),
                work.isDelayedAt(now));
    }
}
