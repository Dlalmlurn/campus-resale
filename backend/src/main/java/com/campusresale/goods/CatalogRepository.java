package com.campusresale.goods;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public CatalogRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<CategorySummary> enabledCategories() {
        return jdbcTemplate.query("""
                        SELECT id, code, name, parent_id
                        FROM categories
                        WHERE enabled = TRUE
                        ORDER BY sort_order, id
                        """,
                (rs, rowNum) -> new CategorySummary(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getObject("parent_id", Long.class)
                )
        );
    }

    public List<TagSummary> enabledTags() {
        return jdbcTemplate.query("""
                        SELECT id, name, description
                        FROM tags
                        WHERE enabled = TRUE
                        ORDER BY id
                        """,
                (rs, rowNum) -> new TagSummary(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description")
                )
        );
    }

    public List<CampusPlaceSummary> enabledCampusPlaces() {
        return jdbcTemplate.query("""
                        SELECT id, campus, name, detail
                        FROM campus_places
                        WHERE enabled = TRUE
                        ORDER BY sort_order, id
                        """,
                (rs, rowNum) -> new CampusPlaceSummary(
                        rs.getLong("id"),
                        rs.getString("campus"),
                        rs.getString("name"),
                        rs.getString("detail")
                )
        );
    }

    public boolean enabledCategoryExists(long categoryId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM categories WHERE id = ? AND enabled = TRUE
                        )
                        """,
                Boolean.class,
                categoryId
        );
        return Boolean.TRUE.equals(exists);
    }

    public boolean categoryProhibited(long categoryId) {
        Boolean prohibited = jdbcTemplate.queryForObject("""
                        SELECT prohibited_flag
                        FROM categories
                        WHERE id = ?
                        """,
                Boolean.class,
                categoryId
        );
        return Boolean.TRUE.equals(prohibited);
    }

    public boolean enabledCampusPlaceExists(long placeId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM campus_places WHERE id = ? AND enabled = TRUE
                        )
                        """,
                Boolean.class,
                placeId
        );
        return Boolean.TRUE.equals(exists);
    }

    public Set<Long> enabledTagIds(Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Set.of();
        }
        return namedParameterJdbcTemplate.queryForList("""
                        SELECT id
                        FROM tags
                        WHERE enabled = TRUE
                          AND id IN (:ids)
                        """,
                Map.of("ids", tagIds),
                Long.class
        ).stream().collect(Collectors.toSet());
    }
}
