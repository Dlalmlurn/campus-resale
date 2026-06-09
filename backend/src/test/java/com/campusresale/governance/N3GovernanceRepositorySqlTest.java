// 文件功能：守护治理仓储中的动态 SQL 拼接，避免举报处理状态流水写入再次出现空格缺失。
package com.campusresale.governance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class N3GovernanceRepositorySqlTest {

    @Test
    void reportStateRecordSqlKeepsWhitespaceBeforeGoodsFilter() {
        String sql = N3GovernanceRepository.reportStateRecordSql("GOODS");

        assertThat(sql).contains("WHERE o.goods_id = ?");
        assertThat(sql).doesNotContain("WHEREo.");
    }

    @Test
    void reportStateRecordSqlKeepsWhitespaceBeforeOrderFilter() {
        String sql = N3GovernanceRepository.reportStateRecordSql("ORDER");

        assertThat(sql).contains("WHERE o.id = ?");
        assertThat(sql).doesNotContain("WHEREo.");
    }
}
