package net.bracits.tendersense.eligibility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class EligibilityEngine {
    public Evaluation evaluate(Profile profile, TenderRequirements tender) {
        List<RuleResult> results = new ArrayList<>();
        if (tender.minimumTurnover() == null) {
            results.add(new RuleResult("TURNOVER", Outcome.NOT_VERIFIABLE, "Tender does not state a minimum turnover"));
        } else if (profile.turnover().compareTo(tender.minimumTurnover()) >= 0) {
            results.add(new RuleResult("TURNOVER", Outcome.PASS, "Turnover requirement met"));
        } else {
            results.add(new RuleResult("TURNOVER", Outcome.FAIL, "Required turnover exceeds BracIT profile turnover"));
        }

        var activeCertifications = profile.certifications().stream()
            .filter(c -> c.validUntil() == null || !c.validUntil().isBefore(LocalDate.now()))
            .map(c -> normalize(c.name())).toList();
        for (String required : tender.requiredCertifications()) {
            boolean found = activeCertifications.contains(normalize(required));
            results.add(new RuleResult("CERTIFICATION", found ? Outcome.PASS : Outcome.FAIL,
                found ? required + " certification available" : "Missing active certification: " + required));
        }
        if (tender.requiredCertifications().isEmpty())
            results.add(new RuleResult("CERTIFICATION", Outcome.NOT_VERIFIABLE, "Tender states no certification requirement"));

        if (tender.geography() == null || tender.geography().isBlank()) {
            results.add(new RuleResult("GEOGRAPHY", Outcome.NOT_VERIFIABLE, "Tender geography is not stated"));
        } else {
            boolean allowed = profile.geographies().stream().map(EligibilityEngine::normalize).anyMatch(normalize(tender.geography())::equals);
            results.add(new RuleResult("GEOGRAPHY", allowed ? Outcome.PASS : Outcome.FAIL,
                allowed ? "Geography is covered" : "BracIT profile does not cover " + tender.geography()));
        }

        Status status = results.stream().anyMatch(r -> r.outcome() == Outcome.FAIL) ? Status.INELIGIBLE
            : results.stream().anyMatch(r -> r.outcome() == Outcome.NOT_VERIFIABLE) ? Status.NEEDS_VERIFICATION : Status.ELIGIBLE;
        return new Evaluation(status, results);
    }

    private static String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT); }

    public record Profile(BigDecimal turnover, List<Certification> certifications, List<String> geographies) {}
    public record Certification(String name, LocalDate validUntil) {}
    public record TenderRequirements(BigDecimal minimumTurnover, List<String> requiredCertifications, String geography) {}
    public record Evaluation(Status status, List<RuleResult> results) {}
    public record RuleResult(String type, Outcome outcome, String reason) {}
    public enum Outcome { PASS, FAIL, NOT_VERIFIABLE }
    public enum Status { ELIGIBLE, INELIGIBLE, NEEDS_VERIFICATION }
}
