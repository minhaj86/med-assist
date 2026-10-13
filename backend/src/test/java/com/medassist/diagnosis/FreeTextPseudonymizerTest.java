package com.medassist.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;

import com.medassist.diagnosis.context.FreeTextPseudonymizer;
import java.util.List;
import org.junit.jupiter.api.Test;

class FreeTextPseudonymizerTest {

    @Test
    void removesKnownIdentifiersEmailsAndPhoneNumbers() {
        FreeTextPseudonymizer scrubber = new FreeTextPseudonymizer(List.of("Alex", "Example", "SYN-0001"));
        String scrubbed = scrubber.scrub("Alex Example (SYN-0001) says pain began at work; call +44 20 7946 0000 "
                + "or alex.example@example.org");
        assertThat(scrubbed).doesNotContain("Alex").doesNotContain("Example").doesNotContain("SYN-0001")
                .doesNotContain("7946").doesNotContain("@")
                .contains("[patient]").contains("pain began at work");
    }

    @Test
    void leavesClinicalTextIntact() {
        FreeTextPseudonymizer scrubber = new FreeTextPseudonymizer(List.of("Jo", "Sample"));
        assertThat(scrubber.scrub("Pain radiating to the jaw, 8/10")).isEqualTo("Pain radiating to the jaw, 8/10");
    }
}
