package io.kestra.plugin.twilio.notify.mms;

import java.util.List;
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
 * Hits the real Twilio Messages API and sends an actual MMS with a media attachment.
 * Enabled only when all TWILIO_* credentials are present (system property or environment
 * variable), so CI and local unit runs skip it. On a trial account MMS only delivers to
 * US/Canada numbers and the media URL must be publicly reachable.
 */
@KestraTest
@EnabledIf("integrationTestEnabled")
class SendIT {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void sendRealMms() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        Send task = Send.builder()
            .accountSID(Property.ofValue(cred("TWILIO_ACCOUNT_SID")))
            .authToken(Property.ofValue(cred("TWILIO_AUTH_TOKEN")))
            .from(Property.ofValue(cred("TWILIO_FROM_NUMBER")))
            .to(Property.ofValue(cred("TWILIO_TO_NUMBER")))
            .body(Property.ofValue("MMS integration test from the Kestra Twilio plugin."))
            .mediaUrls(Property.ofValue(List.of("https://demo.twilio.com/owl.png")))
            .build();

        Send.Output output = task.run(runContext);

        assertThat(output.getSid(), startsWith("MM"));
        assertThat(output.getStatus(), is(not(emptyOrNullString())));
    }

    private static boolean integrationTestEnabled() {
        return notBlank("TWILIO_ACCOUNT_SID")
            && notBlank("TWILIO_AUTH_TOKEN")
            && notBlank("TWILIO_FROM_NUMBER")
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
