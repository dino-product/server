package com.orbit.schedule.application.service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.orbit.schedule.application.port.in.query.dto.LabelInfo;
import com.orbit.schedule.application.port.in.query.dto.StaffInfo;
import com.orbit.schedule.application.port.in.query.dto.TechnicianInfo;
import com.orbit.schedule.application.port.out.LoadStaffProfilesPort;
import com.orbit.schedule.application.port.out.LoadTechnicianProfilesPort;
import com.orbit.schedule.application.port.out.LoadWorkTypeLabelsPort;
import com.orbit.schedule.application.port.out.StaffProfile;
import com.orbit.schedule.application.port.out.TechnicianProfile;
import com.orbit.schedule.application.port.out.TypeLabel;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.PlannedSchedule;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * 조회 응답에 담을 작업들의 표시 정보(담당기사·미리 입력한 담당기사, 등록자, 작업유형)를 포트마다 한 번에 찾아 두고 꺼내 쓴다. 찾을 대상이 없는 포트는 부르지
 * 않는다. 찾지 못한 대상은 식별자만 담는다. 삭제된 작업유형은 작업이 시작되기 전(등록·배정됨·작업전)이면 유형 없음으로 보이고, 작업중·완료·취소 작업에는
 * 삭제 전 이름·색 그대로 보인다([작업] §11).
 */
final class DisplayInfos {

    private static final Set<WorkStatus> NOT_STARTED =
            EnumSet.of(WorkStatus.REGISTERED, WorkStatus.PENDING_ACCEPTANCE, WorkStatus.ACCEPTED);

    private final Map<TechnicianId, TechnicianProfile> technicians;
    private final Map<MembershipId, StaffProfile> staff;
    private final Map<WorkTypeId, TypeLabel> workTypes;

    private DisplayInfos(
            Map<TechnicianId, TechnicianProfile> technicians,
            Map<MembershipId, StaffProfile> staff,
            Map<WorkTypeId, TypeLabel> workTypes) {
        this.technicians = technicians;
        this.staff = staff;
        this.workTypes = workTypes;
    }

    static DisplayInfos of(
            LoadTechnicianProfilesPort technicianProfiles,
            LoadStaffProfilesPort staffProfiles,
            LoadWorkTypeLabelsPort workTypeLabels,
            OrganizationId organizationId,
            Collection<Work> works) {
        Set<TechnicianId> technicianIds = new HashSet<>();
        Set<MembershipId> registrarIds = new HashSet<>();
        Set<WorkTypeId> workTypeIds = new HashSet<>();
        for (Work work : works) {
            work.schedule().map(WorkSchedule::technicianId).ifPresent(technicianIds::add);
            work.plannedSchedule().map(PlannedSchedule::technicianId).ifPresent(technicianIds::add);
            registrarIds.add(work.registrarId());
            work.workType().ifPresent(workTypeIds::add);
        }
        return new DisplayInfos(
                findAll(technicianIds, ids -> technicianProfiles.findProfiles(organizationId, ids)),
                findAll(registrarIds, ids -> staffProfiles.findProfiles(organizationId, ids)),
                findAll(workTypeIds, ids -> workTypeLabels.findLabels(organizationId, ids)));
    }

    /** 일정의 담당기사 표시 정보. */
    TechnicianInfo assignee(WorkSchedule schedule) {
        return technician(schedule.technicianId());
    }

    /** 등록 때 미리 입력한 담당기사의 표시 정보. 미리 입력한 담당기사가 없으면 null. */
    TechnicianInfo plannedTechnician(PlannedSchedule planned) {
        return planned == null || planned.technicianId() == null ? null : technician(planned.technicianId());
    }

    private TechnicianInfo technician(TechnicianId technicianId) {
        TechnicianProfile profile = technicians.get(technicianId);
        return profile == null ? new TechnicianInfo(technicianId.value(), null, null) : technicianInfo(profile);
    }

    /** 담당기사의 기사유형. 기사가 없거나 유형이 없거나 찾지 못했으면 비어 있다. */
    Optional<TypeLabel> technicianType(TechnicianId technicianId) {
        return Optional.ofNullable(technicians.get(technicianId)).map(TechnicianProfile::technicianType);
    }

    StaffInfo registrar(Work work) {
        MembershipId registrarId = work.registrarId();
        StaffProfile profile = staff.get(registrarId);
        return profile == null
                ? new StaffInfo(registrarId.value(), null, null, null)
                : new StaffInfo(registrarId.value(), profile.name(), profile.owner(), label(profile.staffType()));
    }

    /** 화면에 보일 작업유형. 작업유형이 없거나, 시작 전 작업의 유형이 삭제됐으면 비어 있다. 작업유형 조건도 이 값으로 거른다. */
    Optional<WorkTypeId> shownWorkType(Work work) {
        return work.workType()
                .filter(workTypeId -> !NOT_STARTED.contains(work.status())
                        || !Optional.ofNullable(workTypes.get(workTypeId))
                                .map(TypeLabel::deleted)
                                .orElse(false));
    }

    /** 화면에 보일 작업유형의 표시 정보({@link #shownWorkType}). 없으면 null. */
    LabelInfo workType(Work work) {
        return shownWorkType(work)
                .map(workTypeId -> Optional.ofNullable(workTypes.get(workTypeId))
                        .map(DisplayInfos::label)
                        .orElseGet(() -> new LabelInfo(workTypeId.value(), null, null)))
                .orElse(null);
    }

    static TechnicianInfo technicianInfo(TechnicianProfile profile) {
        return new TechnicianInfo(profile.technicianId().value(), profile.name(), label(profile.technicianType()));
    }

    private static LabelInfo label(TypeLabel label) {
        return label == null ? null : new LabelInfo(label.id(), label.name(), label.color());
    }

    private static <K, V> Map<K, V> findAll(Set<K> ids, Function<Set<K>, Map<K, V>> find) {
        return ids.isEmpty() ? Map.of() : find.apply(Set.copyOf(ids));
    }
}
