package com.campusresale.intelligence;

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
}
