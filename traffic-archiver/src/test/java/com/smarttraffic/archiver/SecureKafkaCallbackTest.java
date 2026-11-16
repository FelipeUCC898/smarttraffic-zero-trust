package com.smarttraffic.archiver;

import org.apache.kafka.common.utils.Utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SecureKafkaCallbackTest {
    @Test void kafkaCanInstantiateTheConfiguredCallback() {
        // Kafka instantiates this class reflectively from another package.
        assertNotNull(Utils.newInstance(SecureKafkaConsumerConfiguration.TokenCallback.class));
    }
}
