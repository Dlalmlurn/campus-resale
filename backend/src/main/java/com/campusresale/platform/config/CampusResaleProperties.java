// 文件功能：集中绑定 campus-resale.* 应用配置，供安全、存储、AI 等基础设施模块读取。
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

    /**
     * CORS 白名单配置，生产环境应收敛到真实 HTTPS 域名和端口。
     */
    public record Cors(List<String> allowedOrigins) {
    }

    /**
     * 对象存储连接配置，当前由 MinIO 实现读取并用于头像、商品图和认证材料。
     */
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
