package com.smarttraffic.processor.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.smarttraffic.processor.application.model.ProcessingResult.Outcome;
import com.smarttraffic.processor.domain.model.CongestionState;
import com.smarttraffic.processor.domain.model.ZoneTrafficState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessingResultTest {
    private static final UUID EVENT_ID = UUID.fromString("ca620302-913d-49de-af36-bb1173016f9c");
    private static final ZoneTrafficState STATE = new ZoneTrafficState("north",
            Instant.parse("2026-01-01T00:00:00Z"), BigDecimal.ZERO, CongestionState.DESPEJADA, null);

    @Test
    void processedResultPreservesCorrelationAndTheDomainState() {
        ProcessingResult result = ProcessingResult.processed(EVENT_ID, STATE);

        assertThat(result.eventId()).isEqualTo(EVENT_ID);
        assertThat(result.outcome()).isEqualTo(Outcome.PROCESSED);
        assertThat(result.state()).contains(STATE);
    }

    @Test
    void ignoredResultHasNoStateToPublish() {
        ProcessingResult result = ProcessingResult.ignored(EVENT_ID);

        assertThat(result.eventId()).isEqualTo(EVENT_ID);
        assertThat(result.outcome()).isEqualTo(Outcome.IGNORED);
        assertThat(result.state()).isEmpty();
    }

    @Test
    void rejectsMissingIdentityOutcomeOrOptionalContainer() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ProcessingResult(null, Outcome.IGNORED, Optional.empty())).withMessageContaining("eventId");
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ProcessingResult(EVENT_ID, null, Optional.empty())).withMessageContaining("outcome");
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ProcessingResult(EVENT_ID, Outcome.IGNORED, null)).withMessageContaining("state");
    }

    @Test
    void rejectsProcessedWithoutStateAndIgnoredWithState() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ProcessingResult(EVENT_ID, Outcome.PROCESSED, Optional.empty()));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ProcessingResult(EVENT_ID, Outcome.IGNORED, Optional.of(STATE)));
    }

    @Test
    void factoriesCannotBypassTheRequiredFields() {
        assertThatIllegalArgumentException().isThrownBy(() -> ProcessingResult.processed(null, STATE));
        assertThatIllegalArgumentException().isThrownBy(() -> ProcessingResult.ignored(null));
        assertThatIllegalArgumentException().isThrownBy(() -> ProcessingResult.processed(EVENT_ID, null));
    }
}
