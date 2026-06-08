package com.campusresale.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 守护敏感访问日志的 result 取值，回归举报处理 500
 * （误传 "SUCCESS" 触发 ck_sensitive_access_logs_result 约束 → DataIntegrityViolationException）。
 */
class AuditLogRepositoryTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AuditLogRepository repository = new AuditLogRepository(jdbcTemplate, new ObjectMapper());

    @Test
    void rejectsIllegalResultBeforeHittingDatabase() {
        assertThatThrownBy(() ->
                repository.recordSensitiveAccess(1L, "REPORT_EVIDENCE", 9L, "处理举报证据", "SUCCESS", "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ALLOWED/DENIED/FAILED");

        // 非法值应早失败，绝不发起 INSERT。
        org.mockito.Mockito.verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void persistsAllowedResult() {
        repository.recordSensitiveAccess(1L, "REPORT_EVIDENCE", 9L, "处理举报证据", "ALLOWED", "127.0.0.1");

        verify(jdbcTemplate).update(any(String.class), eq(1L), eq("REPORT_EVIDENCE"), eq(9L),
                eq("处理举报证据"), eq("ALLOWED"), eq("127.0.0.1"), any());
    }

    @Test
    void allLegalResultsAccepted() {
        for (String result : new String[]{"ALLOWED", "DENIED", "FAILED"}) {
            assertThat(result).isNotBlank();
            repository.recordSensitiveAccess(1L, "REPORT_EVIDENCE", 9L, "reason", result, "127.0.0.1");
        }
    }
}
