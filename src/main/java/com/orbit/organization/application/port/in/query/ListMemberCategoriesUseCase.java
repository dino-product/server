package com.orbit.organization.application.port.in.query;

import java.util.List;

import com.orbit.organization.application.port.in.query.dto.ListMemberCategoriesQuery;
import com.orbit.organization.application.port.in.query.dto.MemberCategoryInfo;

public interface ListMemberCategoriesUseCase {

    List<MemberCategoryInfo> list(ListMemberCategoriesQuery query);
}
