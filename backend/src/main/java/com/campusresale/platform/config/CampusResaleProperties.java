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
        @Valid Storage storage
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
}
