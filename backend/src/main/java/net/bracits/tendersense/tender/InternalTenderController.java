package net.bracits.tendersense.tender;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/internal/tenders")
public class InternalTenderController {
    private final TenderIngestionService ingestion;
    private final String token;

    public InternalTenderController(TenderIngestionService ingestion, @Value("${app.internal-token}") String token) {
        this.ingestion = ingestion;
        this.token = token;
    }

    @PostMapping
    Map<String, Object> upsert(@RequestHeader("X-Internal-Token") String supplied, @Valid @RequestBody TenderInput input) {
        if (!java.security.MessageDigest.isEqual(token.getBytes(), supplied.getBytes()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return Map.of("id", ingestion.upsert(input));
    }

    public record TenderInput(@NotBlank String source, @NotBlank String externalId, @NotBlank String title,
        String procuringEntity, @NotBlank String description, String sourceUrl, LocalDate publishDate,
        LocalDate deadlineDate, String geography, @PositiveOrZero BigDecimal estimatedValue,
        @Pattern(regexp = "[A-Z]{3}") String estimatedValueCurrency, BigDecimal requiredTurnover,
        List<String> requiredCertifications) {
        public TenderInput { if (requiredCertifications == null) requiredCertifications = List.of(); }
    }
}
