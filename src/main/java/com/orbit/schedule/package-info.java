@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "shared::error",
            "shared::openapi",
            "shared::persistence",
            "organization",
            "auth",
            "profile",
            "media"
        })
package com.orbit.schedule;
