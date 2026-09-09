package net.bracits.tendersense.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.security.Principal;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import net.bracits.tendersense.tender.TenderProcessingService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TenderProcessingService processor;

    public ProfileController(JdbcTemplate jdbc, ObjectMapper json, TenderProcessingService processor) {
        this.jdbc = jdbc;
        this.json = json;
        this.processor = processor;
    }

    @GetMapping
    Map<String, Object> current() {
        var rows = jdbc.query("SELECT * FROM bracit_profiles ORDER BY version DESC LIMIT 1", (rs, row) -> profile(rs));
        if (rows.isEmpty()) return Map.of("configured", false, "history", List.of());
        var result = new java.util.LinkedHashMap<>(rows.getFirst());
        result.put("configured", true);
        result.put("history", jdbc.queryForList("SELECT version,created_at FROM bracit_profiles ORDER BY version DESC"));
        return result;
    }

    @PutMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> update(@Valid @RequestBody ProfileRequest request, Principal principal) throws JsonProcessingException {
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, principal.getName());
        Integer version = jdbc.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM bracit_profiles", Integer.class);
        String services = json.writeValueAsString(request.services());
        String projects = json.writeValueAsString(request.pastProjects());
        String certifications = json.writeValueAsString(request.certifications());
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO bracit_profiles(version,turnover_amount,currency,services,past_projects,certifications,geographies,created_by)
                VALUES (?,?,?,?::jsonb,?::jsonb,?::jsonb,?,?)
                """, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, version);
            ps.setBigDecimal(2, request.turnoverAmount());
            ps.setString(3, request.currency().toUpperCase());
            ps.setString(4, services);
            ps.setString(5, projects);
            ps.setString(6, certifications);
            Array geographies = connection.createArrayOf("text", request.geographies().toArray());
            ps.setArray(7, geographies);
            ps.setLong(8, userId);
            return ps;
        });
        jdbc.update("UPDATE tenders SET status='NEW' WHERE deadline_date IS NULL OR deadline_date >= CURRENT_DATE");
        processor.processToday();
        return Map.of("version", version);
    }

    private Map<String, Object> profile(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            return Map.ofEntries(
                Map.entry("id", rs.getLong("id")), Map.entry("version", rs.getInt("version")),
                Map.entry("turnoverAmount", rs.getBigDecimal("turnover_amount")), Map.entry("currency", rs.getString("currency")),
                Map.entry("services", json.readValue(rs.getString("services"), List.class)),
                Map.entry("pastProjects", json.readValue(rs.getString("past_projects"), List.class)),
                Map.entry("certifications", json.readValue(rs.getString("certifications"), List.class)),
                Map.entry("geographies", List.of((String[]) rs.getArray("geographies").getArray())),
                Map.entry("createdAt", rs.getTimestamp("created_at").toInstant()));
        } catch (JsonProcessingException e) { throw new java.sql.SQLException(e); }
    }

    public record ServiceLine(@NotBlank String name, @NotBlank String description) {}
    public record PastProject(@NotBlank String title, String client, String sector, Integer year, @NotBlank String description) {}
    public record Certification(@NotBlank String name, String issuingBody, LocalDate validUntil) {}
    public record ProfileRequest(@NotNull @DecimalMin("0") BigDecimal turnoverAmount, @NotBlank String currency,
        @NotEmpty List<ServiceLine> services, List<PastProject> pastProjects,
        List<Certification> certifications, @NotEmpty List<String> geographies) {
        public ProfileRequest {
            pastProjects = pastProjects == null ? List.of() : pastProjects;
            certifications = certifications == null ? List.of() : certifications;
        }
    }
}
