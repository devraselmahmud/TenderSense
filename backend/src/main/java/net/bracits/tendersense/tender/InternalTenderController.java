package net.bracits.tendersense.tender;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;
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
@RequestMapping("/api/internal/tenders")
public class InternalTenderController {
    private final JdbcTemplate jdbc;
    private final TenderProcessingService processor;
    private final String token;

    public InternalTenderController(JdbcTemplate jdbc, TenderProcessingService processor, @Value("${app.internal-token}") String token) {
        this.jdbc = jdbc;
        this.processor = processor;
        this.token = token;
    }

    @PostMapping
    Map<String, Object> upsert(@RequestHeader("X-Internal-Token") String supplied, @Valid @RequestBody TenderInput input) {
        if (!java.security.MessageDigest.isEqual(token.getBytes(), supplied.getBytes()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        Long id = jdbc.query(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO tenders(source,external_id,title,procuring_entity,description,source_url,publish_date,deadline_date,geography,required_turnover,required_certifications)
                VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(source,external_id) DO UPDATE SET title=excluded.title,procuring_entity=excluded.procuring_entity,
                description=excluded.description,source_url=excluded.source_url,publish_date=excluded.publish_date,deadline_date=excluded.deadline_date,
                geography=excluded.geography,required_turnover=excluded.required_turnover,required_certifications=excluded.required_certifications,status='NEW',updated_at=now()
                RETURNING id
                """);
            ps.setString(1, input.source()); ps.setString(2, input.externalId()); ps.setString(3, input.title());
            ps.setString(4, input.procuringEntity()); ps.setString(5, input.description()); ps.setString(6, input.sourceUrl());
            ps.setDate(7, input.publishDate() == null ? null : Date.valueOf(input.publishDate()));
            ps.setDate(8, input.deadlineDate() == null ? null : Date.valueOf(input.deadlineDate()));
            ps.setString(9, input.geography()); ps.setBigDecimal(10, input.requiredTurnover());
            Array certifications = connection.createArrayOf("text", input.requiredCertifications().toArray());
            ps.setArray(11, certifications); return ps;
        }, (rs, row) -> rs.getLong(1)).getFirst();
        processor.process(id);
        return Map.of("id", id);
    }

    public record TenderInput(@NotBlank String source, @NotBlank String externalId, @NotBlank String title,
        String procuringEntity, @NotBlank String description, String sourceUrl, LocalDate publishDate,
        LocalDate deadlineDate, String geography, BigDecimal requiredTurnover, List<String> requiredCertifications) {
        public TenderInput { if (requiredCertifications == null) requiredCertifications = List.of(); }
    }
}
