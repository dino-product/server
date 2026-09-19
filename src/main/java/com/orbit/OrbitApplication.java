package com.orbit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "Orbit", sharedModules = "shared")
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class OrbitApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrbitApplication.class, args);
    }
}
