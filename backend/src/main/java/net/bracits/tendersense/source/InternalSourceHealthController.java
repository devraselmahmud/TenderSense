package net.bracits.tendersense.source;

import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/internal/source-health")
public class InternalSourceHealthController {
    private final JdbcTemplate jdbc;
    private final String token;

    public InternalSourceHealthController(JdbcTemplate jdbc, @Value("${app.internal-token}") String token) {
        this.jdbc = jdbc;
        this.token = token;
    }

    @PostMapping
    void update(@RequestHeader("X-Internal-Token") String supplied, @RequestBody HealthUpdate request) {
        if (!java.security.MessageDigest.isEqual(token.getBytes(), supplied.getBytes()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        jdbc.update("""
            UPDATE source_health SET last_run_at=?, last_success_at=CASE WHEN ? IS NULL THEN ? ELSE last_success_at END,
              records_pulled=?, last_error=? WHERE source=?
            """, request.at(), request.error(), request.at(), request.records(), request.error(), request.source());
    }

    record HealthUpdate(String source, int records, String error, OffsetDateTime at) {}
}
