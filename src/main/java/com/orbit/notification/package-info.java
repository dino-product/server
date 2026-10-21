@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "shared::error",
            "shared::openapi",
            "shared::persistence",
            "schedule",
            "organization",
            "auth"
        })
package com.orbit.notification;
