package net.bracits.tendersense.tender;

import java.sql.Array;
import java.sql.Date;
import java.sql.PreparedStatement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TenderIngestionService {
    private final JdbcTemplate jdbc;
    private final TenderProcessingService processor;

    public TenderIngestionService(JdbcTemplate jdbc, TenderProcessingService processor) {
        this.jdbc = jdbc;
        this.processor = processor;
    }

    public long upsert(InternalTenderController.TenderInput input) {
        Long id = jdbc.query(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO tenders(source,external_id,title,procuring_entity,description,source_url,publish_date,deadline_date,geography,
                  estimated_value,estimated_value_currency,required_turnover,required_certifications)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(source,external_id) DO UPDATE SET title=excluded.title,procuring_entity=excluded.procuring_entity,
                description=excluded.description,source_url=excluded.source_url,publish_date=excluded.publish_date,deadline_date=excluded.deadline_date,
                geography=excluded.geography,estimated_value=excluded.estimated_value,estimated_value_currency=excluded.estimated_value_currency,
                required_turnover=excluded.required_turnover,required_certifications=excluded.required_certifications,status='NEW',updated_at=now()
                RETURNING id
                """);
            ps.setString(1, input.source());
            ps.setString(2, input.externalId());
            ps.setString(3, input.title());
            ps.setString(4, input.procuringEntity());
            ps.setString(5, input.description());
            ps.setString(6, input.sourceUrl());
            ps.setDate(7, input.publishDate() == null ? null : Date.valueOf(input.publishDate()));
            ps.setDate(8, input.deadlineDate() == null ? null : Date.valueOf(input.deadlineDate()));
            ps.setString(9, input.geography());
            ps.setBigDecimal(10, input.estimatedValue());
            ps.setString(11, input.estimatedValueCurrency());
            ps.setBigDecimal(12, input.requiredTurnover());
            Array certifications = connection.createArrayOf("text", input.requiredCertifications().toArray());
            ps.setArray(13, certifications);
            return ps;
        }, (rs, row) -> rs.getLong(1)).getFirst();
        processor.process(id);
        return id;
    }
}
