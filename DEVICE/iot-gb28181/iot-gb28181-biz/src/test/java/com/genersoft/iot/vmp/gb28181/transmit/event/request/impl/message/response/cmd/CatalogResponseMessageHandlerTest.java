package com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.response.cmd;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class CatalogResponseMessageHandlerTest {

    @Test
    void empty_catalog_queue_does_not_open_a_database_transaction() {
        CatalogResponseMessageHandler handler = new CatalogResponseMessageHandler();
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        ReflectionTestUtils.setField(handler, "transactionTemplate", transactionTemplate);

        handler.executeTaskQueue();

        verifyNoInteractions(transactionTemplate);
    }

    @Test
    void druid_creator_thread_is_primed_before_a_transient_database_outage() throws IOException {
        Pattern initialSizeZero = Pattern.compile("(?m)^\\s{8}initial-size:\\s*0(?:\\s+#.*)?$");
        for (String profile : new String[]{"local", "dev", "prod"}) {
            String resource = "/application-" + profile + ".yaml";
            try (InputStream stream = Objects.requireNonNull(
                    getClass().getResourceAsStream(resource),
                    resource + " not found"
            )) {
                String yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(
                        initialSizeZero.matcher(yaml).find(),
                        resource + " must let the creator thread establish the first connection"
                );
            }
        }
    }
}
