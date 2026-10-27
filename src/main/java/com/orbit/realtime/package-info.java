@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "shared::error",
            "shared::openapi",
            "shared::security",
            "organization",
            "auth",
            "schedule",
            "notification"
        })
package com.orbit.realtime;
