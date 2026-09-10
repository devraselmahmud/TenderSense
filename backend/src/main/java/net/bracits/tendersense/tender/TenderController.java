package net.bracits.tendersense.tender;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/tenders")
public class TenderController {
    private final JdbcTemplate jdbc;
    public TenderController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    List<Map<String, Object>> shortlist(@RequestParam(defaultValue = "false") boolean publishedToday) {
        String dateFilter = publishedToday ? "AND (t.publish_date = ? OR (t.source='UPLOAD' AND (t.ingested_at AT TIME ZONE 'Asia/Dhaka')::date=?))" : "";
        String sql = """
            WITH ranked AS (
              SELECT t.*,
                ROW_NUMBER() OVER (
                  PARTITION BY lower(trim(t.title)),lower(trim(coalesce(t.procuring_entity,''))),
                    t.deadline_date,md5(trim(coalesce(t.description,'')))
                  ORDER BY t.id
                ) AS duplicate_rank
              FROM tenders t
              WHERE t.status='SCORED'
                AND t.profile_version=(SELECT MAX(version) FROM bracit_profiles)
                AND t.grade IN ('S','A','B')
                AND t.eligibility_status IN ('ELIGIBLE','NEEDS_VERIFICATION')
                AND t.estimated_value >= 100000
                AND t.estimated_value_currency = 'BDT'
                %s
            )
            SELECT t.id,t.source,t.title,t.procuring_entity AS "procuringEntity",t.publish_date AS "publishDate",
              t.deadline_date AS "deadlineDate",t.grade,t.eligibility_status AS "eligibilityStatus",
              t.eligibility_reason AS "eligibilityReason",t.summary,t.status,
              (SELECT decision FROM bid_decisions d WHERE d.tender_id=t.id ORDER BY decided_at DESC LIMIT 1) AS decision
            FROM ranked t
            WHERE t.duplicate_rank=1
            ORDER BY CASE grade WHEN 'S' THEN 1 WHEN 'A' THEN 2 WHEN 'B' THEN 3 ELSE 4 END, deadline_date NULLS LAST
            """.formatted(dateFilter);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Dhaka"));
        return publishedToday ? jdbc.queryForList(sql, today, today) : jdbc.queryForList(sql);
    }

    @GetMapping("/{id}")
    Map<String, Object> detail(@PathVariable long id) {
        var rows = jdbc.queryForList("""
            SELECT id,source,external_id AS "externalId",title,procuring_entity AS "procuringEntity",description,source_url AS "sourceUrl",
              publish_date AS "publishDate",deadline_date AS "deadlineDate",geography,required_turnover AS "requiredTurnover",
              required_certifications AS "requiredCertifications",similarity_score AS "similarityScore",matched_segment AS "matchedSegment",
              grade,eligibility_status AS "eligibilityStatus",eligibility_reason AS "eligibilityReason",summary,status
            FROM tenders WHERE id=?
            """, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var result = new java.util.LinkedHashMap<>(rows.getFirst());
        java.sql.Array certifications = (java.sql.Array) result.get("requiredCertifications");
        try {
            result.put("requiredCertifications", certifications == null ? List.of() : List.of((String[]) certifications.getArray()));
        } catch (java.sql.SQLException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read certifications", e);
        }
        result.put("ruleResults", jdbc.queryForList("SELECT rule_type AS type,outcome,reason FROM tender_rule_results WHERE tender_id=? ORDER BY id", id));
        result.put("decisions", jdbc.queryForList("""
            SELECT d.decision,d.note,d.decided_at AS "decidedAt",u.name AS "userName" FROM bid_decisions d JOIN users u ON u.id=d.user_id
            WHERE tender_id=? ORDER BY decided_at DESC
            """, id));
        return result;
    }

    @PostMapping("/{id}/decision")
    @ResponseStatus(HttpStatus.CREATED)
    void decide(@PathVariable long id, @Valid @RequestBody DecisionRequest request, Principal principal) {
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, principal.getName());
        int changed = jdbc.update("INSERT INTO bid_decisions(tender_id,user_id,decision,note) SELECT id,?,?,? FROM tenders WHERE id=?",
            userId, request.decision().name(), request.note(), id);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    record DecisionRequest(@NotNull Decision decision, String note) {}
    enum Decision { BID, HOLD, SKIP }
}
