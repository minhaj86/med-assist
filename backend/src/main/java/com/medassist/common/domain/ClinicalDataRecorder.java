package com.medassist.common.domain;

import com.medassist.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Creates provenance for newly recorded clinical data on behalf of the current user. */
@Component
public class ClinicalDataRecorder {

    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public ClinicalDataRecorder(CurrentUserProvider currentUser, Clock clock) {
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * @param source            origin of the value (defaults to CLINICIAN)
     * @param sourceDetail      free-text detail, e.g. device or interface name
     * @param aiProposalReference when set, the value was proposed by the AI assistant and is being explicitly
     *                          confirmed by the current clinician
     */
    public RecordProvenance provenance(DataProvenance source, String sourceDetail, String aiProposalReference) {
        Instant now = Instant.now(clock);
        String userId = currentUser.currentUserId();
        if (aiProposalReference != null && !aiProposalReference.isBlank()) {
            return RecordProvenance.confirmedAiProposal("Confirmed AI proposal: " + aiProposalReference, userId, now);
        }
        DataProvenance effective = source == null ? DataProvenance.CLINICIAN : source;
        if (effective == DataProvenance.AI_INFERRED) {
            throw new com.medassist.common.error.DomainRuleViolationException(
                    "AI-inferred data can only be recorded by confirming an assistant proposal");
        }
        return RecordProvenance.recorded(effective, sourceDetail, userId, now);
    }

    public Instant now() {
        return Instant.now(clock);
    }

    public String currentUserId() {
        return currentUser.currentUserId();
    }
}
