package com.orbit.schedule.adapter.in.web.search;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import com.orbit.schedule.application.port.in.query.dto.LabelInfo;
import com.orbit.schedule.application.port.in.query.dto.StaffInfo;
import com.orbit.schedule.application.port.in.query.dto.TechnicianInfo;
import com.orbit.schedule.application.port.in.query.dto.WorkExportInfo;
import com.orbit.schedule.application.port.in.query.dto.WorkSearchInfo;
import com.orbit.schedule.domain.PaymentMethod;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 작업내역 출력 결과를 xlsx 한 장으로 쓴다. 열은 작업 목록 열에 고객명·금액·결제 방식을 더한 것이고, 시각은 요청한 시간대로 바꿔 글자로 쓴다. 값이 없는 칸은 빈 셀로
 * 두고, 기사·등록자는 있는데 이름을 찾지 못하면 {@link #UNKNOWN_NAME}으로 쓴다.
 */
final class WorkExportWorkbook {

    static final List<String> HEADERS =
            List.of("작업명", "상태", "담당기사", "작업유형", "예정시간", "등록자", "최근 수정일", "고객명", "금액", "결제 방식");

    static final String UNKNOWN_NAME = "이름 없음";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private WorkExportWorkbook() {}

    /** 출력 시각의 날짜를 붙인 파일 이름. */
    static String fileName(WorkExportInfo info, ZoneId zone) {
        return "작업내역_" + FILE_DATE.format(info.exportedAt().atZone(zone)) + ".xlsx";
    }

    static byte[] write(WorkExportInfo info, ZoneId zone) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Workbook workbook = new Workbook(out, "Orbit", "1.0")) {
            Worksheet sheet = workbook.newWorksheet("작업내역");
            for (int column = 0; column < HEADERS.size(); column++) {
                sheet.value(0, column, HEADERS.get(column));
                sheet.style(0, column).bold().set();
            }
            sheet.freezePane(0, 1);
            int row = 1;
            for (WorkExportInfo.Row work : info.rows()) {
                writeRow(sheet, row++, work, zone);
            }
            workbook.finish();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write the work export workbook", e);
        }
        return out.toByteArray();
    }

    private static void writeRow(Worksheet sheet, int row, WorkExportInfo.Row export, ZoneId zone) {
        WorkSearchInfo.WorkSummary work = export.work();
        text(sheet, row, 0, work.name());
        text(sheet, row, 1, statusLabel(work.status()));
        text(sheet, row, 2, technicianName(work.technician()));
        text(sheet, row, 3, labelName(work.workType()));
        text(sheet, row, 4, scheduledTime(work.startTime(), work.endTime(), zone));
        text(sheet, row, 5, registrar(work.registrar()));
        text(sheet, row, 6, DATE_TIME.format(work.lastModifiedAt().atZone(zone)));
        text(sheet, row, 7, work.customerName());
        if (export.fee() != null) {
            sheet.value(row, 8, export.fee());
            sheet.style(row, 8).format("#,##0").set();
        }
        text(sheet, row, 9, paymentLabel(export.paymentMethod()));
    }

    /** 값이 없으면 셀을 만들지 않아 엑셀에서 빈 셀로 보이게 한다. */
    private static void text(Worksheet sheet, int row, int column, String value) {
        if (value != null) {
            sheet.value(row, column, value);
        }
    }

    /**
     * 같은 날이면 "2026-10-21 09:00 ~ 11:00", 날을 넘기면 끝도 날짜와 함께 쓴다. 끝을 모르면(예상소요시간을 정하지 않은 대기함 작업) "2026-10-21 09:00
     * ~"이고, 시작을 모르면 null(빈 셀)이다.
     */
    private static String scheduledTime(Instant start, Instant end, ZoneId zone) {
        if (start == null) {
            return null;
        }
        ZonedDateTime localStart = start.atZone(zone);
        if (end == null) {
            return DATE_TIME.format(localStart) + " ~";
        }
        ZonedDateTime localEnd = end.atZone(zone);
        DateTimeFormatter endFormat = localStart.toLocalDate().equals(localEnd.toLocalDate()) ? TIME : DATE_TIME;
        return DATE_TIME.format(localStart) + " ~ " + endFormat.format(localEnd);
    }

    /** "이름(직원유형)". 유형이 없으면 이름만 쓴다(총관리자 표시는 하지 않는다). */
    private static String registrar(StaffInfo registrar) {
        String name = registrar.name() == null ? UNKNOWN_NAME : registrar.name();
        String type = labelName(registrar.staffType());
        return type == null ? name : name + "(" + type + ")";
    }

    /** 담당기사가 없으면(담당기사를 정하지 않은 대기함 작업, 배정 전에 취소된 작업) null(빈 셀)이다. */
    private static String technicianName(TechnicianInfo technician) {
        if (technician == null) {
            return null;
        }
        return technician.name() == null ? UNKNOWN_NAME : technician.name();
    }

    private static String labelName(LabelInfo label) {
        return label == null ? null : label.name();
    }

    private static String statusLabel(WorkStatus status) {
        return switch (status) {
            case REGISTERED -> "등록";
            case PENDING_ACCEPTANCE -> "배정됨";
            case ACCEPTED -> "작업전";
            case IN_PROGRESS -> "작업중";
            case COMPLETED -> "완료";
            case CANCELLED -> "취소";
        };
    }

    private static String paymentLabel(PaymentMethod method) {
        if (method == null) {
            return null;
        }
        return switch (method) {
            case ON_SITE_CARD -> "현장 카드";
            case ON_SITE_CASH -> "현장 현금";
            case BANK_TRANSFER -> "계좌이체";
            case INVOICE -> "청구서 발행(후불)";
        };
    }
}
