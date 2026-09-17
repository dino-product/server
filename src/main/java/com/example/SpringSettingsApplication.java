package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "Spring Settings", sharedModules = "shared")
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class SpringSettingsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringSettingsApplication.class, args);
    }
}
