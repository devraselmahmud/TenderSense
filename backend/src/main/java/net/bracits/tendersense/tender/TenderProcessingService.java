package net.bracits.tendersense.tender;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Array;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.bracits.tendersense.eligibility.EligibilityEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TenderProcessingService {
    private static final Logger log = LoggerFactory.getLogger(TenderProcessingService.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final EligibilityEngine eligibility;
    private final RestClient python;

    public TenderProcessingService(JdbcTemplate jdbc, ObjectMapper json, EligibilityEngine eligibility,
        RestClient.Builder rest, @Value("${app.python-url}") String pythonUrl, @Value("${app.internal-token}") String token) {
        this.jdbc = jdbc;
        this.json = json;
        this.eligibility = eligibility;
        this.python = rest.baseUrl(pythonUrl).requestFactory(new SimpleClientHttpRequestFactory())
            .defaultHeader("X-Internal-Token", token).build();
    }

    @Async
    public void processToday() {
        jdbc.queryForList(
            "SELECT id FROM tenders WHERE publish_date=?",
            Long.class,
            LocalDate.now(ZoneId.of("Asia/Dhaka"))
        ).forEach(id -> {
            try {
                process(id);
            } catch (Exception e) {
                log.warn("Tender processing failed for id {}: {}", id, safeMessage(e));
            }
        });
    }

    public void process(long tenderId) {
        var profiles = jdbc.queryForList("SELECT * FROM bracit_profiles ORDER BY version DESC LIMIT 1");
        if (profiles.isEmpty()) {
            jdbc.update("UPDATE tenders SET status='NEEDS_PROFILE', eligibility_status='NEEDS_VERIFICATION', eligibility_reason='Configure BracIT profile to score this tender' WHERE id=?", tenderId);
            return;
        }
        Map<String, Object> profile = profiles.getFirst();
        Map<String, Object> tender = jdbc.queryForMap("SELECT * FROM tenders WHERE id=?", tenderId);
        try {
            List<Map<String, Object>> certs = json.readValue(profile.get("certifications").toString(), new TypeReference<>() {});
            List<String> profileCerts = certs.stream().map(c -> c.get("name").toString()).toList();
            LocalDate today = LocalDate.now();
            List<EligibilityEngine.Certification> activeCerts = certs.stream().map(c -> new EligibilityEngine.Certification(
                c.get("name").toString(), c.get("validUntil") == null ? null : LocalDate.parse(c.get("validUntil").toString()))).toList();
            List<String> geographies = List.of((String[]) ((Array) profile.get("geographies")).getArray());
            List<String> requiredCerts = List.of((String[]) ((Array) tender.get("required_certifications")).getArray());
            var evaluation = eligibility.evaluate(
                new EligibilityEngine.Profile((BigDecimal) profile.get("turnover_amount"), activeCerts, geographies),
                new EligibilityEngine.TenderRequirements((BigDecimal) tender.get("required_turnover"), requiredCerts, (String) tender.get("geography")));

            List<Map<String, Object>> services = json.readValue(profile.get("services").toString(), new TypeReference<>() {});
            List<Map<String, Object>> projects = json.readValue(profile.get("past_projects").toString(), new TypeReference<>() {});
            List<String> segments = new ArrayList<>();
            services.forEach(s -> segments.add(s.get("name") + ": " + s.get("description")));
            projects.forEach(p -> segments.add(p.get("title") + ": " + p.get("description")));
            profileCerts.forEach(c -> segments.add("Certification: " + c));
            String matchRequest = json.writeValueAsString(Map.of(
                "tender_text", tender.get("title") + "\n" + tender.get("description"), "profile_segments", segments));
            MatchResponse match = python.post().uri("/internal/match-score").contentType(MediaType.APPLICATION_JSON)
                .body(matchRequest).retrieve().body(MatchResponse.class);
            double score = match == null ? 0 : match.score();
            String grade = grade(score);
            String reason = evaluation.results().stream().filter(r -> r.outcome() != EligibilityEngine.Outcome.PASS)
                .map(EligibilityEngine.RuleResult::reason).reduce((a, b) -> a + "; " + b).orElse("All stated eligibility requirements pass");
            String summary = match == null ? "No semantic match result available. " + reason : summarize(tender, match.segment(), reason, grade, profile.get("version"));

            jdbc.update("DELETE FROM tender_rule_results WHERE tender_id=?", tenderId);
            evaluation.results().forEach(r -> jdbc.update("INSERT INTO tender_rule_results(tender_id,rule_type,outcome,reason) VALUES (?,?,?,?)",
                tenderId, r.type(), r.outcome().name(), r.reason()));
            jdbc.update("""
                UPDATE tenders SET similarity_score=?,matched_segment=?,grade=?,eligibility_status=?,eligibility_reason=?,summary=?,profile_version=?,status='SCORED',updated_at=now()
                WHERE id=?
                """, score, match == null ? null : match.segment(), grade, evaluation.status().name(), reason, summary, profile.get("version"), tenderId);
        } catch (Exception e) {
            log.warn("Tender processing failed for id {}: {}", tenderId, e.getMessage());
            jdbc.update("UPDATE tenders SET status='PROCESSING_FAILED', eligibility_reason=?, updated_at=now() WHERE id=?", safeMessage(e), tenderId);
        }
    }

    private String grade(double score) {
        return jdbc.query("SELECT grade FROM grade_thresholds WHERE minimum_score <= ? ORDER BY minimum_score DESC LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : "C", score);
    }

    private String summarize(Map<String, Object> tender, String segment, String eligibilityReason, String grade, Object profileVersion) {
        try {
            String summaryRequest = json.writeValueAsString(Map.of(
                "title", tender.get("title"), "description", tender.get("description"), "matched_segment", segment,
                "eligibility_reason", eligibilityReason, "grade", grade, "profile_version", profileVersion));
            SummaryResponse response = python.post().uri("/internal/summarize").contentType(MediaType.APPLICATION_JSON)
                .body(summaryRequest).retrieve().body(SummaryResponse.class);
            return response == null ? "Matched profile capability: " + segment + ". " + eligibilityReason : response.summary();
        } catch (Exception ignored) {
            return "Matched profile capability: " + segment + ". " + eligibilityReason;
        }
    }

    private static String safeMessage(Exception e) {
        String name = e.getClass().getSimpleName();
        return "Processing failed: " + name;
    }

    record MatchResponse(double score, String segment) {}
    record SummaryResponse(String summary) {}
}
