@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "shared::error",
            "shared::openapi",
            "shared::persistence",
            "schedule",
            "organization",
            "auth",
            "profile"
        })
package com.orbit.notification;
