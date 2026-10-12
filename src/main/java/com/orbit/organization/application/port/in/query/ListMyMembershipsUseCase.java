package com.orbit.organization.application.port.in.query;

import java.util.List;

import com.orbit.organization.application.port.in.query.dto.ListMyMembershipsQuery;
import com.orbit.organization.application.port.in.query.dto.MyMembershipInfo;

public interface ListMyMembershipsUseCase {

    List<MyMembershipInfo> list(ListMyMembershipsQuery query);
}
