package com.medassist.configuration;

import com.medassist.ai.assistant.AssistantMessageRepository;
import com.medassist.ai.execution.AiExecutionRepository;
import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Applies the configured retention periods to technical AI payloads and assistant transcripts. */
@Component
public class DataRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionJob.class);

    private final RetentionProperties properties;
    private final AiExecutionRepository executions;
    private final AssistantMessageRepository messages;
    private final AuditService auditService;
    private final Clock clock;

    public DataRetentionJob(RetentionProperties properties, AiExecutionRepository executions,
                            AssistantMessageRepository messages, AuditService auditService, Clock clock) {
        this.properties = properties;
        this.executions = executions;
        this.messages = messages;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Scheduled(cron = "${medassist.retention.cron:0 30 3 * * *}")
    @Transactional
    public void purge() {
        Instant now = Instant.now(clock);
        int raw = properties.aiRawResponseDays() > 0
                ? executions.purgeRawResponsesBefore(now.minus(Duration.ofDays(properties.aiRawResponseDays()))) : 0;
        int transcripts = properties.assistantTranscriptDays() > 0
                ? messages.purgeBefore(now.minus(Duration.ofDays(properties.assistantTranscriptDays()))) : 0;
        if (raw > 0 || transcripts > 0) {
            log.info("Retention purge: {} AI raw responses, {} assistant messages", raw, transcripts);
            auditService.record(AuditEntry.of(AuditEventType.DATA_RETENTION_PURGE)
                    .entity("RetentionPolicy", "scheduled")
                    .with("aiRawResponses", raw)
                    .with("assistantMessages", transcripts));
        }
    }
}
