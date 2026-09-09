package net.bracits.tendersense;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import net.bracits.tendersense.eligibility.EligibilityEngine;
import org.junit.jupiter.api.Test;

class EligibilityEngineTest {
    private final EligibilityEngine engine = new EligibilityEngine();
    private final EligibilityEngine.Profile profile = new EligibilityEngine.Profile(new BigDecimal("1000000"),
        List.of(new EligibilityEngine.Certification("ISO 27001", LocalDate.now().plusDays(1))), List.of("Bangladesh"));

    @Test void eligibleWhenAllRequirementsPass() {
        var result = engine.evaluate(profile, new EligibilityEngine.TenderRequirements(new BigDecimal("500000"), List.of("iso 27001"), "bangladesh"));
        assertThat(result.status()).isEqualTo(EligibilityEngine.Status.ELIGIBLE);
    }

    @Test void ineligibleWhenAnyRequirementFails() {
        var result = engine.evaluate(profile, new EligibilityEngine.TenderRequirements(new BigDecimal("2000000"), List.of("ISO 9001"), "Nepal"));
        assertThat(result.status()).isEqualTo(EligibilityEngine.Status.INELIGIBLE);
        assertThat(result.results()).filteredOn(r -> r.outcome() == EligibilityEngine.Outcome.FAIL).hasSize(3);
    }

    @Test void needsVerificationWhenRequirementsAreMissing() {
        var result = engine.evaluate(profile, new EligibilityEngine.TenderRequirements(null, List.of(), null));
        assertThat(result.status()).isEqualTo(EligibilityEngine.Status.NEEDS_VERIFICATION);
    }
}
