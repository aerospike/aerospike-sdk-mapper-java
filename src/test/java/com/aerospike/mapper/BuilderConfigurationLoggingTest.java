package com.aerospike.mapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.mapper.tools.AeroMapper;
import com.aerospike.mapper.tools.ClassCache;

class BuilderConfigurationLoggingTest {
    private static final String INVALID_CONFIGURATION = """
            ---
            classes:
              - class: com.example.DoesNotExist
                namespace: test
                set: broken
            """;

    @BeforeEach
    @AfterEach
    void clearClassCache() {
        ClassCache.getInstance().clear();
    }

    @Test
    void rejectsInvalidConfigurationWhenStrictValidationIsEnabled() {
        assertThrows(AerospikeException.class,
                () -> new AeroMapper.Builder(null).withConfiguration(INVALID_CONFIGURATION, false).build());
    }

    @Test
    void ignoresInvalidConfigurationWhenStrictValidationIsDisabled() {
        Logger behaviorLogger = (Logger) LoggerFactory.getLogger("behavior");
        Level originalLevel = behaviorLogger.getLevel();
        boolean originalAdditivity = behaviorLogger.isAdditive();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        behaviorLogger.addAppender(appender);
        behaviorLogger.setAdditive(false);
        behaviorLogger.setLevel(Level.WARN);
        try {
            AeroMapper mapper = assertDoesNotThrow(
                    () -> new AeroMapper.Builder(null).withConfiguration(INVALID_CONFIGURATION, true).build());

            assertNotNull(mapper);
            assertEquals(1, appender.list.size());
            assertEquals(Level.WARN, appender.list.get(0).getLevel());
            assertTrue(appender.list.get(0).getFormattedMessage().contains("Ignoring issue with configuration:"));
            assertTrue(appender.list.get(0).getFormattedMessage()
                    .contains("Cannot find a class with name com.example.DoesNotExist"));
        } finally {
            behaviorLogger.detachAppender(appender);
            appender.stop();
            behaviorLogger.setAdditive(originalAdditivity);
            behaviorLogger.setLevel(originalLevel);
        }
    }
}
