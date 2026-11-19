package com.smarttraffic.archiver;

import com.smarttraffic.archiver.config.*;
import com.smarttraffic.archiver.dto.*;
import com.smarttraffic.archiver.model.*;
import com.smarttraffic.archiver.repository.*;
import com.smarttraffic.archiver.service.*;

import org.apache.kafka.common.utils.Utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SecureKafkaCallbackTest {
    @Test void kafkaCanInstantiateTheConfiguredCallback() {
        // Kafka instantiates this class reflectively from another package.
        assertNotNull(Utils.newInstance(SecureKafkaConsumerConfiguration.TokenCallback.class));
    }
}
