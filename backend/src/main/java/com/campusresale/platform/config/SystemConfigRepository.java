package com.campusresale.platform.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SystemConfigRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SystemConfigRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<String> findValue(String key) {
        List<String> values = jdbcTemplate.queryForList("""
                        SELECT config_value
                        FROM system_configs
                        WHERE config_key = ?
                        """,
                String.class,
                key
        );
        return values.stream().findFirst();
    }

    public int intValue(String key, int defaultValue) {
        return findValue(key)
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException exception) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    public List<String> stringListValue(String key, List<String> defaultValue) {
        return findValue(key)
                .map(value -> {
                    try {
                        return objectMapper.readValue(value, STRING_LIST);
                    } catch (Exception exception) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }
}
