package com.campusresale.intelligence;

import com.campusresale.intelligence.IntelligenceResponses.GoodsAssistResponse;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IntelligenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public IntelligenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int countTodayRequests(long userId, String scenario) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)::int
                        FROM ai_assist_records
                        WHERE user_id = ?
                          AND scenario = ?
                          AND created_at >= date_trunc('day', now())
                        """,
                Integer.class,
                userId,
                scenario
        );
        return count == null ? 0 : count;
    }

    public long createRecord(long userId, String scenario, String inputTitle, String riskLevel, String recommendationReason) {
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO ai_assist_records (
                            user_id,
                            scenario,
                            input_title,
                            risk_level,
                            recommendation_reason
                        )
                        VALUES (?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                userId,
                scenario,
                inputTitle,
                riskLevel,
                recommendationReason
        );
        return id == null ? 0 : id;
    }

    public void updateRecordDetails(
            long recordId,
            String inputTitle,
            String inputDescription,
            String inputPrice,
            GoodsAssistResponse response
    ) {
        jdbcTemplate.update("""
                        UPDATE ai_assist_records
                        SET input_title = ?,
                            input_description = ?,
                            input_price = CAST(? AS numeric),
                            optimized_title = ?,
                            optimized_description = ?,
                            suggested_category_code = ?,
                            suggested_tags_json = CAST(? AS jsonb),
                            risk_reasons_json = CAST(? AS jsonb),
                            audit_reminder = ?
                        WHERE id = ?
                        """,
                inputTitle,
                inputDescription,
                inputPrice,
                response.optimizedTitle(),
                response.optimizedDescription(),
                response.suggestedCategoryCode(),
                jsonArray(response.suggestedTags()),
                jsonArray(response.riskReasons()),
                response.auditReminder(),
                recordId
        );
    }

    private static String jsonArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return "[" + values.stream()
                .map(value -> "\"" + escapeJson(value) + "\"")
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private static String escapeJson(String value) {
        return value == null
                ? ""
                : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
