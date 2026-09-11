package io.kestra.plugin.twilio.notify.sms;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;

import jakarta.inject.Inject;

import static io.kestra.plugin.twilio.notify.TwilioStub.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class SendTest {
    @Inject
    private RunContextFactory runContextFactory;

    private static Send task() {
        return Send.builder()
            .accountSID(Property.ofValue(ACCOUNT_SID))
            .authToken(Property.ofValue(AUTH_TOKEN))
            .from(Property.ofValue("+15005550006"))
            .to(Property.ofValue("+15555550100"))
            .body(Property.ofValue("Hello from Kestra."))
            .build();
    }

    @Test
    void sendSms() throws Exception {
        var http = respondsWith(201, """
            {"sid":"SM1234567890abcdef","status":"queued"}
            """);

        var output = sending(task(), http).run(runContextFactory.of(Map.of()));

        assertThat(output.getSid(), is("SM1234567890abcdef"));
        assertThat(output.getStatus(), is("queued"));

        var params = sentParams(http);
        assertThat(params.get("From"), contains(equalTo("+15005550006")));
        assertThat(params.get("To"), contains(equalTo("+15555550100")));
        assertThat(params.get("Body"), contains(equalTo("Hello from Kestra.")));
    }

    @Test
    void failsOnNon201() {
        var task = sending(task(), respondsWith(400, """
            {"code":21211,"message":"The 'To' number is not a valid phone number.","status":400}
            """));

        var exception = assertThrows(RuntimeException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("not a valid phone number"));
        // the body must never reach the user as a raw byte array
        assertThat(exception.getMessage(), not(containsString("[B@")));
    }
}
