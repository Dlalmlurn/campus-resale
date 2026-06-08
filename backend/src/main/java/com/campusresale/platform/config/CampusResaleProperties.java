package com.campusresale.platform.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "campus-resale")
public record CampusResaleProperties(
        @Valid Cors cors,
        @Valid Storage storage,
        @Valid Ai ai
) {

    public record Cors(List<String> allowedOrigins) {
    }

    public record Storage(
            @NotBlank String endpoint,
            @NotBlank String bucket,
            @NotBlank String accessKey,
            @NotBlank String secretKey
    ) {
    }

    /**
     * AI 发布辅助的 LLM 接入配置。未启用或 apiKey 为空时，业务层退回稳健规则引擎。
     */
    public record Ai(
            boolean enabled,
            String provider,
            String baseUrl,
            String apiKey,
            String model,
            int timeoutMs
    ) {

        /** 是否具备发起 LLM 调用的最小条件：开关打开且配置了 baseUrl 与 apiKey。 */
        public boolean usable() {
            return enabled
                    && baseUrl != null && !baseUrl.isBlank()
                    && apiKey != null && !apiKey.isBlank();
        }
    }
}
