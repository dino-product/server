package com.orbit.schedule.adapter.in.web.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.port.in.query.dto.LabelInfo;
import com.orbit.schedule.application.port.in.query.dto.StaffInfo;
import com.orbit.schedule.application.port.in.query.dto.TechnicianInfo;
import com.orbit.schedule.application.port.in.query.dto.WorkExportInfo;
import com.orbit.schedule.application.port.in.query.dto.WorkSearchInfo;
import com.orbit.schedule.domain.PaymentMethod;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("작업내역 엑셀 파일")
class WorkExportWorkbookTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant EXPORTED_AT = Instant.parse("2026-10-20T15:30:00Z");

    @Test
    @DisplayName("머리글과 작업마다 한 줄을 쓰고, 시각은 요청한 시간대로 바꾸며, 값이 없는 칸은 빈 셀로 둔다")
    void writesHeaderAndRowsInZone() throws IOException {
        WorkExportInfo.Row assigned = new WorkExportInfo.Row(
                summary(
                        "배관 설치",
                        WorkStatus.PENDING_ACCEPTANCE,
                        new TechnicianInfo(3L, "김기사", null),
                        Instant.parse("2026-10-21T00:00:00Z"),
                        Instant.parse("2026-10-21T02:00:00Z"),
                        new LabelInfo(2L, "설치", null),
                        new StaffInfo(1L, "김운영", false, new LabelInfo(5L, "접수", null)),
                        "이정호"),
                120_000L,
                PaymentMethod.ON_SITE_CARD);
        WorkExportInfo.Row overnight = new WorkExportInfo.Row(
                summary(
                        "야간 점검",
                        WorkStatus.COMPLETED,
                        new TechnicianInfo(4L, null, null),
                        Instant.parse("2026-10-21T14:00:00Z"),
                        Instant.parse("2026-10-21T16:00:00Z"),
                        null,
                        new StaffInfo(6L, "박총괄", true, null),
                        null),
                null,
                null);
        WorkExportInfo.Row backlog = new WorkExportInfo.Row(
                summary(
                        "대기 작업",
                        WorkStatus.REGISTERED,
                        null,
                        null,
                        null,
                        null,
                        new StaffInfo(7L, null, null, null),
                        null),
                null,
                PaymentMethod.BANK_TRANSFER);

        WorkExportInfo.Row startOnly = new WorkExportInfo.Row(
                summary(
                        "시작만 정한 작업",
                        WorkStatus.REGISTERED,
                        new TechnicianInfo(3L, "김기사", null),
                        Instant.parse("2026-10-21T00:00:00Z"),
                        null,
                        null,
                        new StaffInfo(1L, "김운영", false, null),
                        null),
                null,
                PaymentMethod.INVOICE);

        List<Row> rows = read(WorkExportWorkbook.write(
                new WorkExportInfo(List.of(assigned, overnight, backlog, startOnly), EXPORTED_AT), SEOUL));

        assertThat(texts(rows.get(0))).containsExactlyElementsOf(WorkExportWorkbook.HEADERS);
        assertThat(texts(rows.get(1)))
                .containsExactly(
                        "배관 설치",
                        "배정됨",
                        "김기사",
                        "설치",
                        "2026-10-21 09:00 ~ 11:00",
                        "김운영(접수)",
                        "2026-10-21 00:30",
                        "이정호",
                        "120000",
                        "현장 카드");
        assertThat(rows.get(1).getCellAsNumber(8)).contains(new BigDecimal("120000"));
        assertThat(texts(rows.get(2)))
                .containsExactly(
                        "야간 점검",
                        "완료",
                        WorkExportWorkbook.UNKNOWN_NAME,
                        "",
                        "2026-10-21 23:00 ~ 2026-10-22 01:00",
                        "박총괄",
                        "2026-10-21 00:30",
                        "",
                        "",
                        "");
        assertThat(texts(rows.get(3)))
                .containsExactly(
                        "대기 작업", "등록", "", "", "", WorkExportWorkbook.UNKNOWN_NAME, "2026-10-21 00:30", "", "", "계좌이체");
        assertThat(rows.get(3).getCell(2)).as("값이 없는 칸은 빈 셀이다").isNull();
        assertThat(texts(rows.get(4)))
                .as("끝을 모르는 대기함 작업은 시작만 쓴다")
                .containsExactly(
                        "시작만 정한 작업",
                        "등록",
                        "김기사",
                        "",
                        "2026-10-21 09:00 ~",
                        "김운영",
                        "2026-10-21 00:30",
                        "",
                        "",
                        "청구서 발행(후불)");
        assertThat(rows).hasSize(5);
    }

    @Test
    @DisplayName("파일 이름은 요청한 시간대의 출력 날짜를 붙인다")
    void namesFileByLocalExportDate() {
        WorkExportInfo info = new WorkExportInfo(List.of(), EXPORTED_AT);

        assertThat(WorkExportWorkbook.fileName(info, SEOUL)).isEqualTo("작업내역_20261021.xlsx");
        assertThat(WorkExportWorkbook.fileName(info, ZoneId.of("UTC"))).isEqualTo("작업내역_20261020.xlsx");
    }

    private static WorkSearchInfo.WorkSummary summary(
            String name,
            WorkStatus status,
            TechnicianInfo technician,
            Instant start,
            Instant end,
            LabelInfo workType,
            StaffInfo registrar,
            String customerName) {
        return new WorkSearchInfo.WorkSummary(
                1L,
                name,
                status,
                technician,
                start,
                end,
                customerName,
                workType,
                registrar,
                EXPORTED_AT.minusSeconds(3600),
                EXPORTED_AT,
                false,
                false);
    }

    private static List<Row> read(byte[] xlsx) throws IOException {
        try (ReadableWorkbook workbook = new ReadableWorkbook(new ByteArrayInputStream(xlsx))) {
            return workbook.getFirstSheet().read();
        }
    }

    private static List<String> texts(Row row) {
        return IntStream.range(0, WorkExportWorkbook.HEADERS.size())
                .mapToObj(column -> column < row.getCellCount() ? row.getCellText(column) : "")
                .map(text -> text == null ? "" : text)
                .toList();
    }
}
