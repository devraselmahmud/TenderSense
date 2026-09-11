package net.bracits.tendersense.tender;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/tenders/uploads")
public class TenderUploadController {
    private final TenderIngestionService ingestion;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RestClient python;

    @Autowired
    public TenderUploadController(TenderIngestionService ingestion, JdbcTemplate jdbc, ObjectMapper json,
        RestClient.Builder rest, @Value("${app.python-url}") String pythonUrl, @Value("${app.internal-token}") String token) {
        this(ingestion, jdbc, json, rest.baseUrl(pythonUrl).requestFactory(new SimpleClientHttpRequestFactory())
            .defaultHeader("X-Internal-Token", token).build());
    }

    TenderUploadController(TenderIngestionService ingestion, JdbcTemplate jdbc, ObjectMapper json, RestClient python) {
        this.ingestion = ingestion;
        this.jdbc = jdbc;
        this.json = json;
        this.python = python;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    UploadResponse upload(@RequestPart("file") MultipartFile file, Principal principal) throws IOException {
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file is empty");
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(file.getBytes()) {
            @Override public String getFilename() { return file.getOriginalFilename(); }
        });
        body.add("uploader", principal.getName());

        ExtractionResponse extracted;
        try {
            extracted = python.post().uri("/internal/uploads/extract")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(ExtractionResponse.class);
        } catch (RestClientResponseException error) {
            throw new ResponseStatusException(error.getStatusCode(), extractionMessage(error), error);
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Document extraction service unavailable", error);
        }
        if (extracted == null || extracted.records() == null || extracted.records().isEmpty())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "No tenders found in uploaded file");

        List<Long> tenderIds = new ArrayList<>();
        for (ExtractedRecord record : extracted.records()) tenderIds.add(ingestion.upsert(record.tender()));
        List<Long> qualifyingIds = tenderIds.stream().filter(id -> Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT status='SCORED' AND grade IN ('S','A','B')
              AND eligibility_status IN ('ELIGIBLE','NEEDS_VERIFICATION')
              AND estimated_value >= (SELECT minimum_tender_budget FROM bracit_profiles WHERE version=tenders.profile_version)
              AND estimated_value_currency='BDT' FROM tenders WHERE id=?
            """, Boolean.class, id))).toList();
        return new UploadResponse(extracted.uploadId(), extracted.fileHash(), tenderIds.size(), qualifyingIds.size(), tenderIds,
            qualifyingIds, extracted.warnings() == null ? List.of() : extracted.warnings());
    }

    private String extractionMessage(RestClientResponseException error) {
        try {
            JsonNode detail = json.readTree(error.getResponseBodyAsString()).path("detail");
            String message = detail.path("message").asText();
            return message.isBlank() ? "Document extraction failed" : message;
        } catch (Exception ignored) {
            return "Document extraction failed";
        }
    }

    record ExtractionResponse(@JsonProperty("upload_id") String uploadId, @JsonProperty("file_hash") String fileHash,
        List<ExtractedRecord> records, List<String> warnings) {}
    record ExtractedRecord(InternalTenderController.TenderInput tender) {}
    record UploadResponse(String uploadId, String fileHash, int importedCount, int qualifyingCount,
        List<Long> tenderIds, List<Long> qualifyingTenderIds, List<String> warnings) {}
}
