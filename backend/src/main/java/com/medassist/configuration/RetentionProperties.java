package com.medassist.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Data retention. Clinical records and the audit trail are retained (jurisdiction-specific retention must be
 * implemented by archival procedures); technical AI payloads and assistant transcripts are purged after the
 * configured number of days. 0 disables a purge.
 */
@ConfigurationProperties(prefix = "medassist.retention")
public record RetentionProperties(@DefaultValue("30") int aiRawResponseDays,
                                  @DefaultValue("365") int assistantTranscriptDays,
                                  @DefaultValue("0 30 3 * * *") String cron) {
}
