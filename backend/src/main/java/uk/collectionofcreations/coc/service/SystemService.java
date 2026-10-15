package uk.collectionofcreations.coc.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/** 系统健康检查 */
@Service
public class SystemService {

    private final JdbcTemplate jdbcTemplate;

    public SystemService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        String db = "ok";
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (one == null || one != 1) {
                db = "error";
            }
        } catch (Exception e) {
            db = "error: " + e.getMessage();
        }
        data.put("status", "ok".equals(db) ? "ok" : "degraded");
        data.put("db", db);
        data.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        data.put("service", "coc-platform-backend");
        data.put("version", "0.1.0");
        return data;
    }
}
