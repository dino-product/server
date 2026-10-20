@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "shared::error",
            "shared::openapi",
            "shared::persistence",
            "organization",
            "auth",
            "media"
        })
package com.orbit.schedule;
