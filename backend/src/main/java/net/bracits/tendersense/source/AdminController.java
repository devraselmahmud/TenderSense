package net.bracits.tendersense.source;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final RestClient python;

    public AdminController(JdbcTemplate jdbc, PasswordEncoder encoder, RestClient.Builder rest,
        @Value("${app.python-url}") String pythonUrl, @Value("${app.internal-token}") String token) {
        this.jdbc = jdbc; this.encoder = encoder;
        this.python = rest.baseUrl(pythonUrl).requestFactory(new SimpleClientHttpRequestFactory())
            .defaultHeader("X-Internal-Token", token).build();
    }

    @GetMapping("/source-health") List<Map<String, Object>> health() { return jdbc.queryForList("SELECT * FROM source_health ORDER BY source"); }
    @GetMapping("/rules") Map<String, Object> rules() { return Map.of("eligibility", jdbc.queryForList("SELECT * FROM eligibility_rules ORDER BY id"), "grades", jdbc.queryForList("SELECT * FROM grade_thresholds ORDER BY minimum_score DESC")); }
    @PutMapping("/rules/{id}") void updateRule(@PathVariable long id, @RequestBody RuleUpdate request) { jdbc.update("UPDATE eligibility_rules SET operator=?,value=?,active=? WHERE id=?", request.operator(), request.value(), request.active(), id); }
    @GetMapping("/users") List<Map<String, Object>> users() { return jdbc.queryForList("SELECT id,name,email,role,enabled,created_at FROM users ORDER BY name"); }

    @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED)
    void addUser(@Valid @RequestBody UserRequest request) {
        jdbc.update("INSERT INTO users(name,email,password_hash,role) VALUES (?,?,?,?)", request.name(), request.email(), encoder.encode(request.password()), request.role().name());
    }

    @PostMapping("/ingestion/run") Map<?, ?> run() {
        return python.post().uri("/ingestion/run").retrieve().body(Map.class);
    }

    record RuleUpdate(@NotBlank String operator, String value, boolean active) {}
    record UserRequest(@NotBlank String name, @Email String email, @NotBlank String password, @NotNull Role role) {}
    enum Role { BD_EXECUTIVE, BD_UNIT_LEAD, ADMIN }
}
