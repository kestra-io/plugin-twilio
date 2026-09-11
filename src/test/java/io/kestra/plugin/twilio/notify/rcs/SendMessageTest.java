package io.kestra.plugin.twilio.notify.rcs;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.twilio.http.HttpClient;
import com.twilio.http.Request;
import com.twilio.http.Response;
import com.twilio.http.TwilioRestClient;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@KestraTest
class SendMessageTest {
    private static final String ACCOUNT_SID = "AC00000000000000000000000000000000";
    private static final String MESSAGING_SERVICE_SID = "MG00000000000000000000000000000000";
    private static final String CONTENT_SID = "HX00000000000000000000000000000000";

    @Inject
    private RunContextFactory runContextFactory;

    private static HttpClient respondsWith(int status, String body) {
        var http = mock(HttpClient.class);
        when(http.reliableRequest(any())).thenReturn(new Response(body, status));

        return http;
    }

    /** A real client with only the transport stubbed, so the SDK still builds and parses everything itself. */
    private static SendMessage sendVia(HttpClient http, SendMessage task) {
        var client = new TwilioRestClient.Builder(ACCOUNT_SID, "test_auth_token")
            .accountSid(ACCOUNT_SID)
            .httpClient(http)
            .build();

        SendMessage spied = spy(task);
        doReturn(client).when(spied).restClient(anyString(), anyString());

        return spied;
    }

    private static SendMessage.SendMessageBuilder<?, ?> task() {
        return SendMessage.builder()
            .accountSID(Property.ofValue(ACCOUNT_SID))
            .authToken(Property.ofValue("test_auth_token"))
            .messagingServiceSid(Property.ofValue(MESSAGING_SERVICE_SID))
            .to(Property.ofValue("+15555550100"));
    }

    private static Map<String, List<String>> sentParams(HttpClient http) {
        var sent = ArgumentCaptor.forClass(Request.class);
        verify(http).reliableRequest(sent.capture());

        return sent.getValue().getPostParams();
    }

    @Test
    void sendRcs() throws Exception {
        var http = respondsWith(201, """
            {"sid":"SM1234567890abcdef","status":"queued"}
            """);

        var output = sendVia(http, task().body(Property.ofValue("Hello from Kestra.")).build())
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

        var output = sendVia(http, task().contentSid(Property.ofValue(CONTENT_SID)).build())
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

        var output = sendVia(http, task().body(Property.ofValue("Delivered either way.")).build())
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
        var task = sendVia(respondsWith(400, """
            {"code":21211,"message":"The 'To' number is not a valid phone number.","more_info":"https://www.twilio.com/docs/errors/21211","status":400}
            """), task().to(Property.ofValue("invalid")).body(Property.ofValue("test")).build());

        var exception = assertThrows(RuntimeException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("not a valid phone number"));
        assertThat(exception.getMessage(), containsString("https://www.twilio.com/docs/errors/21211"));
        assertThat(exception.getMessage(), not(containsString("[B@")));
    }

    @Test
    void failsOnEmptyResponseBody() {
        var task = sendVia(respondsWith(201, ""), task().body(Property.ofValue("test")).build());

        var exception = assertThrows(RuntimeException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("empty body"));
    }

    @Test
    void failsOnUnparseableResponseBody() {
        var task = sendVia(respondsWith(201, "not json"), task().body(Property.ofValue("test")).build());

        var exception = assertThrows(RuntimeException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("unparseable"));
    }

    @Test
    void failsOnInvalidAccountSid() {
        var task = sendVia(
            respondsWith(201, "{}"),
            task().accountSID(Property.ofValue("not-an-account-sid")).body(Property.ofValue("test")).build()
        );

        assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of(Map.of())));
    }

    @Test
    void failsWithoutBodyOrContentSid() {
        var task = sendVia(respondsWith(201, "{}"), task().build());

        var exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("either body or contentSid"));
    }

    @Test
    void failsWithoutSender() {
        var task = sendVia(
            respondsWith(201, "{}"), SendMessage.builder()
                .accountSID(Property.ofValue(ACCOUNT_SID))
                .authToken(Property.ofValue("test_auth_token"))
                .to(Property.ofValue("+15555550100"))
                .body(Property.ofValue("test"))
                .build()
        );

        var exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("either from or messagingServiceSid"));
    }

    @Test
    void failsWhenBothSendersSet() {
        var task = sendVia(
            respondsWith(201, "{}"),
            task().from(Property.ofValue("+15005550006")).body(Property.ofValue("test")).build()
        );

        var exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("mutually exclusive"));
    }

    @Test
    void failsWhenMessagingServiceSidPassedAsFrom() {
        var task = sendVia(
            respondsWith(201, "{}"), SendMessage.builder()
                .accountSID(Property.ofValue(ACCOUNT_SID))
                .authToken(Property.ofValue("test_auth_token"))
                .from(Property.ofValue(MESSAGING_SERVICE_SID))
                .to(Property.ofValue("+15555550100"))
                .body(Property.ofValue("test"))
                .build()
        );

        var exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("messagingServiceSid instead"));
    }
}
