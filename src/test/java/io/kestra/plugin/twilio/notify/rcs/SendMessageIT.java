package io.kestra.plugin.twilio.notify.rcs;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Hits the real Twilio Messages API and sends an actual RCS message (or its SMS fallback).
 * Enabled only when all TWILIO_* credentials are present (system property or environment
 * variable), so CI and local unit runs skip it. TWILIO_MESSAGING_SERVICE_SID must name a
 * Messaging Service with an RCS sender. On a trial account the recipient must be verified.
 */
@KestraTest
@EnabledIf("integrationTestEnabled")
class SendMessageIT {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void sendRealRcs() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        SendMessage task = SendMessage.builder()
            .accountSID(Property.ofValue(cred("TWILIO_ACCOUNT_SID")))
            .authToken(Property.ofValue(cred("TWILIO_AUTH_TOKEN")))
            .messagingServiceSid(Property.ofValue(cred("TWILIO_MESSAGING_SERVICE_SID")))
            .to(Property.ofValue(cred("TWILIO_TO_NUMBER")))
            .body(Property.ofValue("Integration test from the Kestra Twilio plugin."))
            .build();

        SendMessage.Output output = task.run(runContext);

        assertThat(output.getSid(), startsWith("SM"));
        assertThat(output.getStatus(), is(not(emptyOrNullString())));
    }

    private static boolean integrationTestEnabled() {
        return notBlank("TWILIO_ACCOUNT_SID")
            && notBlank("TWILIO_AUTH_TOKEN")
            && notBlank("TWILIO_MESSAGING_SERVICE_SID")
            && notBlank("TWILIO_TO_NUMBER");
    }

    private static String cred(String name) {
        return System.getProperty(name, System.getenv(name));
    }

    private static boolean notBlank(String name) {
        String value = cred(name);
        return value != null && !value.isBlank();
    }
}
