package net.bracits.tendersense.tender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TenderUploadControllerTest {
    @Test
    void forwardsAuthenticatedUploaderAndImportsExtractedTenders() throws Exception {
        TenderIngestionService ingestion = mock(TenderIngestionService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RestClient.Builder rest = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(once(), requestTo("http://python/internal/uploads/extract"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Internal-Token", "secret"))
            .andExpect(content().string(allOf(containsString("lead@bracits.net"), containsString("tenders.xlsx"))))
            .andRespond(withSuccess("""
                {"upload_id":"upload-1","file_hash":"abc","records":[{"tender":{"source":"UPLOAD","externalId":"abc:1","title":"Cloud Security","description":"Security services","requiredCertifications":[]},"raw":{}}],"warnings":[]}
                """, MediaType.APPLICATION_JSON));
        when(ingestion.upsert(any())).thenReturn(42L);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(42L))).thenReturn(true);
        TenderUploadController controller = new TenderUploadController(ingestion, jdbc, new ObjectMapper(),
            rest.baseUrl("http://python").defaultHeader("X-Internal-Token", "secret").build());
        MockMultipartFile file = new MockMultipartFile("file", "tenders.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "content".getBytes(StandardCharsets.UTF_8));
        Principal principal = () -> "lead@bracits.net";

        var response = controller.upload(file, principal);

        assertThat(response.uploadId()).isEqualTo("upload-1");
        assertThat(response.importedCount()).isEqualTo(1);
        assertThat(response.qualifyingCount()).isEqualTo(1);
        assertThat(response.tenderIds()).containsExactly(42L);
        verify(ingestion).upsert(any());
        verify(jdbc).queryForObject(contains("estimated_value >= 100000"), eq(Boolean.class), eq(42L));
        server.verify();
    }
}
