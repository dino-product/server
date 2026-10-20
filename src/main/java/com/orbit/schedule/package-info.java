@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "shared::error",
            "shared::openapi",
            "shared::persistence",
            "organization",
            "auth",
            "profile"
        })
package com.orbit.schedule;
