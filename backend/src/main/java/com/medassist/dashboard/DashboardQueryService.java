package com.medassist.dashboard;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read model for the clinician dashboard (CQRS-style SQL projections across modules; no writes). */
@Service
public class DashboardQueryService {

    public record PatientSummary(UUID id, String externalPatientId, String firstName, String lastName, int ageYears,
                                 String biologicalSex, boolean synthetic) {
    }

    public record LatestAssessment(UUID versionId, int versionNumber, Instant createdAt, String aiStatus,
                                   int criticalAlerts, int urgentAlerts) {
    }

    public record EncounterItem(UUID encounterId, PatientSummary patient, String encounterType, String status,
                                String chiefComplaint, Instant startedAt, String clinicianId, boolean mine,
                                String assessmentWorkflowStatus, LatestAssessment latestAssessment) {
    }

    public record AlertItem(UUID encounterId, PatientSummary patient, String severity, String origin, String title,
                            boolean demonstrationOnly, int versionNumber, Instant assessedAt) {
    }

    public record AssessmentItem(UUID versionId, UUID encounterId, PatientSummary patient, int versionNumber,
                                 Instant createdAt, String aiStatus, int alertCount, String leadingConsideration) {
    }

    public record Dashboard(Instant since, List<EncounterItem> todaysEncounters, List<PatientSummary> recentPatients,
                            List<EncounterItem> incompleteAssessments, List<AlertItem> criticalAlerts,
                            List<AssessmentItem> recentAssessments) {
    }

    private static final String ENCOUNTER_COLUMNS = """
            select e.id as encounter_id, e.encounter_type, e.status, e.chief_complaint, e.started_at, e.clinician_id,
                   p.id as patient_id, p.external_patient_id, p.first_name, p.last_name, p.date_of_birth,
                   p.biological_sex, p.synthetic, a.workflow_status,
                   v.id as version_id, v.version_number, v.created_at as assessed_at, v.ai_status,
                   (select count(*) from safety_alerts s where s.assessment_version_id = v.id and s.severity = 'CRITICAL') as critical,
                   (select count(*) from safety_alerts s where s.assessment_version_id = v.id and s.severity = 'URGENT') as urgent
            from encounters e
            join patients p on p.id = e.patient_id
            left join assessments a on a.encounter_id = e.id
            left join lateral (select av.id, av.version_number, av.created_at, av.ai_status from assessment_versions av
                               where av.encounter_id = e.id order by av.version_number desc limit 1) v on true
            """;

    private final JdbcClient jdbc;

    public DashboardQueryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(String clinicianId, Instant since) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<EncounterItem> todays = jdbc.sql(ENCOUNTER_COLUMNS + " where e.started_at >= :since order by e.started_at desc limit 50")
                .param("since", java.sql.Timestamp.from(since))
                .query((rs, n) -> encounter(rs, clinicianId, today)).list();
        List<EncounterItem> incomplete = jdbc.sql(ENCOUNTER_COLUMNS + """
                         where e.status = 'IN_PROGRESS' and (a.workflow_status is null or a.workflow_status <> 'FINALIZED')
                         order by e.started_at desc limit 20""")
                .query((rs, n) -> encounter(rs, clinicianId, today)).list();
        List<PatientSummary> recentPatients = jdbc.sql("""
                        select patient_id, external_patient_id, first_name, last_name, date_of_birth, biological_sex, synthetic
                        from (select distinct on (p.id) p.id as patient_id, p.external_patient_id, p.first_name, p.last_name,
                                     p.date_of_birth, p.biological_sex, p.synthetic, e.started_at
                              from encounters e join patients p on p.id = e.patient_id
                              where e.clinician_id = :me
                              order by p.id, e.started_at desc) recent
                        order by started_at desc limit 10""")
                .param("me", clinicianId)
                .query((rs, n) -> patient(rs, today)).list();
        List<AlertItem> alerts = jdbc.sql("""
                        select e.id as encounter_id, p.id as patient_id, p.external_patient_id, p.first_name, p.last_name,
                               p.date_of_birth, p.biological_sex, p.synthetic, s.severity, s.origin, s.title,
                               s.demonstration_only, v.version_number, v.created_at
                        from encounters e
                        join patients p on p.id = e.patient_id
                        join lateral (select av.id, av.version_number, av.created_at from assessment_versions av
                                      where av.encounter_id = e.id order by av.version_number desc limit 1) v on true
                        join safety_alerts s on s.assessment_version_id = v.id
                        where e.status = 'IN_PROGRESS' and s.severity = 'CRITICAL'
                        order by v.created_at desc limit 20""")
                .query((rs, n) -> new AlertItem(rs.getObject("encounter_id", UUID.class), patient(rs, today),
                        rs.getString("severity"), rs.getString("origin"), rs.getString("title"),
                        rs.getBoolean("demonstration_only"), rs.getInt("version_number"),
                        rs.getTimestamp("created_at").toInstant())).list();
        List<AssessmentItem> recentAssessments = jdbc.sql("""
                        select v.id as version_id, e.id as encounter_id, p.id as patient_id, p.external_patient_id,
                               p.first_name, p.last_name, p.date_of_birth, p.biological_sex, p.synthetic,
                               v.version_number, v.created_at, v.ai_status,
                               (select count(*) from safety_alerts s where s.assessment_version_id = v.id) as alert_count,
                               (select d.condition_name from differential_diagnoses d where d.assessment_version_id = v.id
                                order by d.rank limit 1) as leading
                        from assessment_versions v
                        join encounters e on e.id = v.encounter_id
                        join patients p on p.id = v.patient_id
                        where v.created_by = :me
                        order by v.created_at desc limit 10""")
                .param("me", clinicianId)
                .query((rs, n) -> new AssessmentItem(rs.getObject("version_id", UUID.class),
                        rs.getObject("encounter_id", UUID.class), patient(rs, today), rs.getInt("version_number"),
                        rs.getTimestamp("created_at").toInstant(), rs.getString("ai_status"), rs.getInt("alert_count"),
                        rs.getString("leading"))).list();
        return new Dashboard(since, todays, recentPatients, incomplete, alerts, recentAssessments);
    }

    private static EncounterItem encounter(ResultSet rs, String clinicianId, LocalDate today) throws SQLException {
        UUID versionId = rs.getObject("version_id", UUID.class);
        LatestAssessment latest = versionId == null ? null : new LatestAssessment(versionId, rs.getInt("version_number"),
                rs.getTimestamp("assessed_at").toInstant(), rs.getString("ai_status"), rs.getInt("critical"),
                rs.getInt("urgent"));
        String clinician = rs.getString("clinician_id");
        return new EncounterItem(rs.getObject("encounter_id", UUID.class), patient(rs, today),
                rs.getString("encounter_type"), rs.getString("status"), rs.getString("chief_complaint"),
                rs.getTimestamp("started_at").toInstant(), clinician, clinician.equals(clinicianId),
                rs.getString("workflow_status"), latest);
    }

    private static PatientSummary patient(ResultSet rs, LocalDate today) throws SQLException {
        LocalDate dob = rs.getDate("date_of_birth").toLocalDate();
        return new PatientSummary(rs.getObject("patient_id", UUID.class), rs.getString("external_patient_id"),
                rs.getString("first_name"), rs.getString("last_name"), Period.between(dob, today).getYears(),
                rs.getString("biological_sex"), rs.getBoolean("synthetic"));
    }
}
