package com.orbit.schedule.application.port.in.query;

import com.orbit.schedule.application.port.in.query.dto.ExportWorksQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkExportInfo;

public interface ExportWorksUseCase {

    WorkExportInfo export(ExportWorksQuery query);
}
