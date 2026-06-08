package com.campusresale.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 守护通知未读筛选 SQL 拼接，回归"只看未读"500（read_at IS NULLORDER BY 非法语法）。
 * 这是纯字符串测试，不依赖数据库，但能在拼接漏空格时立刻失败——正是之前 @WebMvcTest mock 漏掉的点。
 */
class NotificationRepositorySqlTest {

    @Test
    void listSqlSeparatesUnreadClauseFromOrderBy() {
        String sql = NotificationRepository.buildListSql(true);

        assertThat(sql).doesNotContain("NULLORDER");
        assertThat(sql).contains("read_at IS NULL");
        assertThat(sql).contains(" ORDER BY created_at DESC, id DESC");
        assertThat(sql).contains(" LIMIT ? OFFSET ?");
    }

    @Test
    void listSqlOmitsUnreadClauseWhenNotFiltering() {
        String sql = NotificationRepository.buildListSql(false);

        assertThat(sql).doesNotContain("read_at IS NULL");
        assertThat(sql).contains(" ORDER BY created_at DESC, id DESC");
    }

    @Test
    void countSqlAppliesUnreadClauseWithLeadingSpace() {
        String unread = NotificationRepository.buildCountSql(true);
        String all = NotificationRepository.buildCountSql(false);

        assertThat(unread).contains("? AND read_at IS NULL");
        assertThat(all).doesNotContain("read_at IS NULL");
    }
}
