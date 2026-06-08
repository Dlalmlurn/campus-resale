// 文件功能：后端应用启动入口，加载 Spring Boot、定时任务与 campus-resale.* 配置绑定。
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

    /**
     * 启动校园二手交易平台后端服务。
     */
    public static void main(String[] args) {
        SpringApplication.run(CampusResaleApplication.class, args);
    }
}
