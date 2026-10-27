package com.orbit.profile.application.port.in.query;

import com.orbit.profile.application.port.in.query.dto.GetProfileQuery;
import com.orbit.profile.application.port.in.query.dto.ProfileInfo;

public interface GetProfileUseCase {

    ProfileInfo getProfile(GetProfileQuery query);
}
