package net.bracits.tendersense.tender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import net.bracits.tendersense.eligibility.EligibilityEngine;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

class TenderShortlistTest {
    @Test
    void shortlistUsesLatestProfileAndExcludesLowOrIneligibleTenders() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(LocalDate.class))).thenReturn(List.of());

        new TenderController(jdbc).shortlist(true);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(LocalDate.class));
        assertThat(sql.getValue())
            .contains("t.status='SCORED'")
            .contains("t.profile_version=(SELECT MAX(version) FROM bracit_profiles)")
            .contains("t.grade IN ('S','A','B')")
            .contains("t.eligibility_status IN ('ELIGIBLE','NEEDS_VERIFICATION')")
            .contains("t.publish_date = ?");
    }

    @Test
    void todayBatchProcessesEveryStoredTender() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(Long.class), any(LocalDate.class))).thenReturn(List.of(3L, 7L));
        List<Long> processed = new ArrayList<>();
        TenderProcessingService service = new TenderProcessingService(
            jdbc,
            new ObjectMapper(),
            new EligibilityEngine(),
            RestClient.builder(),
            "http://localhost",
            "token"
        ) {
            @Override
            public void process(long tenderId) {
                processed.add(tenderId);
            }
        };

        service.processToday();

        assertThat(processed).containsExactly(3L, 7L);
    }
}
