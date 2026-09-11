package io.kestra.plugin.twilio.notify.rcs;

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
class SendMessageTest {
    private static final String MESSAGING_SERVICE_SID = "MG00000000000000000000000000000000";
    private static final String CONTENT_SID = "HX00000000000000000000000000000000";

    @Inject
    private RunContextFactory runContextFactory;

    private static SendMessage.SendMessageBuilder<?, ?> task() {
        return SendMessage.builder()
            .accountSID(Property.ofValue(ACCOUNT_SID))
            .authToken(Property.ofValue(AUTH_TOKEN))
            .messagingServiceSid(Property.ofValue(MESSAGING_SERVICE_SID))
            .to(Property.ofValue("+15555550100"));
    }

    @Test
    void sendRcs() throws Exception {
        var http = respondsWith(201, """
            {"sid":"SM1234567890abcdef","status":"queued"}
            """);

        var output = sending(task().body(Property.ofValue("Hello from Kestra.")).build(), http)
            .run(runContextFactory.of(Map.of()));

        assertThat(output.getSid(), is("SM1234567890abcdef"));
        assertThat(output.getStatus(), is("queued"));

        var params = sentParams(http);
        assertThat(params.get("MessagingServiceSid"), contains(equalTo(MESSAGING_SERVICE_SID)));
        assertThat(params.get("To"), contains(equalTo("+15555550100")));
        assertThat(params.get("Body"), contains(equalTo("Hello from Kestra.")));
        assertThat(params, not(hasKey("ContentSid")));
    }

    @Test
    void sendRcsWithContentTemplate() throws Exception {
        var http = respondsWith(201, """
            {"sid":"SMcontent0000000000","status":"accepted"}
            """);

        var output = sending(task().contentSid(Property.ofValue(CONTENT_SID)).build(), http)
            .run(runContextFactory.of(Map.of()));

        assertThat(output.getSid(), is("SMcontent0000000000"));
        assertThat(output.getStatus(), is("accepted"));

        var params = sentParams(http);
        assertThat(params.get("ContentSid"), contains(equalTo(CONTENT_SID)));
        // no empty Body may be sent, a content template relies on its absence
        assertThat(params, not(hasKey("Body")));
    }

    /**
     * Twilio decides RCS versus SMS server side, so the contract this pins is that the task sends no fallback
     * configuration of its own and accepts a response describing an SMS-delivered message.
     */
    @Test
    void requestsNoFallbackConfigurationAndAcceptsSmsResponse() throws Exception {
        var http = respondsWith(201, """
            {"sid":"SMfallback000000000","status":"queued","num_segments":"1"}
            """);

        var output = sending(task().body(Property.ofValue("Delivered either way.")).build(), http)
            .run(runContextFactory.of(Map.of()));

        assertThat(output.getSid(), is("SMfallback000000000"));
        assertThat(output.getStatus(), is("queued"));
        assertThat(
            sentParams(http).keySet(),
            everyItem(not(matchesRegex(".*(Fallback|SmsFallback|Channel|ContentRetention).*")))
        );
    }

    @Test
    void failsOnNon201() {
        var task = sending(
            task().to(Property.ofValue("invalid")).body(Property.ofValue("test")).build(),
            respondsWith(400, """
                {"code":21211,"message":"The 'To' number is not a valid phone number.","more_info":"https://www.twilio.com/docs/errors/21211","status":400}
                """)
        );

        var exception = assertThrows(RuntimeException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("not a valid phone number"));
        assertThat(exception.getMessage(), containsString("https://www.twilio.com/docs/errors/21211"));
        assertThat(exception.getMessage(), not(containsString("[B@")));
    }

    @Test
    void failsOnEmptyResponseBody() {
        var task = sending(task().body(Property.ofValue("test")).build(), respondsWith(201, ""));

        var exception = assertThrows(RuntimeException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("empty body"));
    }

    @Test
    void failsOnUnparseableResponseBody() {
        var task = sending(task().body(Property.ofValue("test")).build(), respondsWith(201, "not json"));

        var exception = assertThrows(RuntimeException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("unparseable"));
    }

    @Test
    void failsOnInvalidAccountSid() {
        var task = sending(
            task().accountSID(Property.ofValue("not-an-account-sid")).body(Property.ofValue("test")).build(),
            respondsWith(201, "{}")
        );

        assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of(Map.of())));
    }

    @Test
    void failsWithoutBodyOrContentSid() {
        var task = sending(task().build(), respondsWith(201, "{}"));

        var exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("either body or contentSid"));
    }

    @Test
    void failsWithoutSender() {
        var task = sending(
            SendMessage.builder()
                .accountSID(Property.ofValue(ACCOUNT_SID))
                .authToken(Property.ofValue(AUTH_TOKEN))
                .to(Property.ofValue("+15555550100"))
                .body(Property.ofValue("test"))
                .build(),
            respondsWith(201, "{}")
        );

        var exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("either from or messagingServiceSid"));
    }

    @Test
    void failsWhenBothSendersSet() {
        var task = sending(
            task().from(Property.ofValue("+15005550006")).body(Property.ofValue("test")).build(),
            respondsWith(201, "{}")
        );

        var exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("mutually exclusive"));
    }

    @Test
    void failsWhenMessagingServiceSidPassedAsFrom() {
        var task = sending(
            SendMessage.builder()
                .accountSID(Property.ofValue(ACCOUNT_SID))
                .authToken(Property.ofValue(AUTH_TOKEN))
                .from(Property.ofValue(MESSAGING_SERVICE_SID))
                .to(Property.ofValue("+15555550100"))
                .body(Property.ofValue("test"))
                .build(),
            respondsWith(201, "{}")
        );

        var exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("messagingServiceSid instead"));
    }
}
