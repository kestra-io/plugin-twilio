package io.kestra.plugin.twilio.notify.sms;

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
class SendTest {
    private static final String ACCOUNT_SID = "AC00000000000000000000000000000000";

    @Inject
    private RunContextFactory runContextFactory;

    private static HttpClient respondsWith(int status, String body) {
        HttpClient http = mock(HttpClient.class);
        when(http.reliableRequest(any())).thenReturn(new Response(body, status));

        return http;
    }

    /** A real client with only the transport stubbed, so the SDK still builds and parses everything itself. */
    private static Send sendVia(HttpClient http) {
        var client = new TwilioRestClient.Builder(ACCOUNT_SID, "test_auth_token")
            .accountSid(ACCOUNT_SID)
            .httpClient(http)
            .build();

        Send task = Send.builder()
            .accountSID(Property.ofValue(ACCOUNT_SID))
            .authToken(Property.ofValue("test_auth_token"))
            .from(Property.ofValue("+15005550006"))
            .to(Property.ofValue("+15555550100"))
            .body(Property.ofValue("Hello from Kestra."))
            .build();

        Send spied = spy(task);
        doReturn(client).when(spied).restClient(anyString(), anyString());

        return spied;
    }

    private static Map<String, List<String>> sentParams(HttpClient http) {
        var sent = ArgumentCaptor.forClass(Request.class);
        verify(http).reliableRequest(sent.capture());

        return sent.getValue().getPostParams();
    }

    @Test
    void sendSms() throws Exception {
        var http = respondsWith(201, """
            {"sid":"SM1234567890abcdef","status":"queued"}
            """);

        var output = sendVia(http).run(runContextFactory.of(Map.of()));

        assertThat(output.getSid(), is("SM1234567890abcdef"));
        assertThat(output.getStatus(), is("queued"));

        var params = sentParams(http);
        assertThat(params.get("From"), contains(equalTo("+15005550006")));
        assertThat(params.get("To"), contains(equalTo("+15555550100")));
        assertThat(params.get("Body"), contains(equalTo("Hello from Kestra.")));
    }

    @Test
    void failsOnNon201() {
        var task = sendVia(respondsWith(400, """
            {"code":21211,"message":"The 'To' number is not a valid phone number.","status":400}
            """));

        var exception = assertThrows(RuntimeException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("not a valid phone number"));
        // the body must never reach the user as a raw byte array
        assertThat(exception.getMessage(), not(containsString("[B@")));
    }
}
