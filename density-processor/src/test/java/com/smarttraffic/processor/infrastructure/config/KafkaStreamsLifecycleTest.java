package com.smarttraffic.processor.infrastructure.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import org.apache.kafka.streams.KafkaStreams;
import org.junit.jupiter.api.Test;

class KafkaStreamsLifecycleTest {
    @Test void startsOnceClosesWithTimeoutAndDoesNotDeleteState() {
        var streams = mock(KafkaStreams.class);
        when(streams.close(Duration.ofSeconds(5))).thenReturn(true);
        var lifecycle = new KafkaStreamsLifecycle(streams, Duration.ofSeconds(5));
        lifecycle.start(); lifecycle.start(); assertThat(lifecycle.isRunning()).isTrue();
        lifecycle.stop(); assertThat(lifecycle.isRunning()).isFalse();
        verify(streams).start(); verify(streams).close(Duration.ofSeconds(5)); verify(streams, never()).cleanUp();
    }
    @Test void failedStartDoesNotClaimRunning() {
        var streams = mock(KafkaStreams.class); doThrow(new IllegalStateException("fail")).when(streams).start();
        var lifecycle = new KafkaStreamsLifecycle(streams, Duration.ofSeconds(5));
        assertThatThrownBy(lifecycle::start).isInstanceOf(IllegalStateException.class);
        assertThat(lifecycle.isRunning()).isFalse();
    }
    @Test void rejectsMissingDependencies() {
        assertThatIllegalArgumentException().isThrownBy(() -> new KafkaStreamsLifecycle(null, Duration.ofSeconds(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new KafkaStreamsLifecycle(mock(KafkaStreams.class), Duration.ZERO));
    }
}
