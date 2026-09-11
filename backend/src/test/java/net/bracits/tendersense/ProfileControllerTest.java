package net.bracits.tendersense.profile;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileControllerTest {
    @Test
    void rejectsNegativeMinimumTenderBudget() {
        var request = new ProfileController.ProfileRequest(
            BigDecimal.ZERO,
            "BDT",
            BigDecimal.valueOf(-1),
            List.of(new ProfileController.ServiceLine("Software", "Development")),
            List.of(),
            List.of(),
            List.of("Bangladesh")
        );

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("minimumTenderBudget");
        }
    }
}
