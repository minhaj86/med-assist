package com.medassist.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import com.medassist.assessment.domain.FeedbackAction;
import com.medassist.assessment.domain.ReviewState;
import org.junit.jupiter.api.Test;

class ReviewStateTest {

    @Test
    void derivesHumanInTheLoopStates() {
        assertThat(ReviewState.from(null, false, false)).isEqualTo(ReviewState.AI_GENERATED);
        assertThat(ReviewState.from(FeedbackAction.ACCEPTED, false, false)).isEqualTo(ReviewState.ACCEPTED);
        assertThat(ReviewState.from(FeedbackAction.ACCEPTED, true, false)).isEqualTo(ReviewState.MODIFIED);
        assertThat(ReviewState.from(FeedbackAction.NOT_RELEVANT, false, false)).isEqualTo(ReviewState.REJECTED);
        assertThat(ReviewState.from(FeedbackAction.NEEDS_MORE_INFORMATION, false, false)).isEqualTo(ReviewState.REVIEWED);
        assertThat(ReviewState.from(FeedbackAction.ACCEPTED, false, true)).isEqualTo(ReviewState.FINALIZED);
        assertThat(ReviewState.from(FeedbackAction.REJECTED, false, true)).isEqualTo(ReviewState.REJECTED);
    }
}
