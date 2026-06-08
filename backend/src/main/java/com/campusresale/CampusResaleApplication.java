package com.campusresale;

import com.campusresale.platform.config.CampusResaleProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(CampusResaleProperties.class)
public class CampusResaleApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusResaleApplication.class, args);
    }
}
