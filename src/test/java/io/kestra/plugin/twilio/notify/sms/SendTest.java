package io.kestra.plugin.twilio.notify.sms;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

    /** A real client with a stubbed transport, so the SDK still builds and parses everything itself. */
    private static TwilioRestClient clientReturning(String body, int status) {
        var http = mock(com.twilio.http.HttpClient.class);
        when(http.reliableRequest(any())).thenReturn(new Response(body, status));

        return new TwilioRestClient.Builder(ACCOUNT_SID, "test_auth_token")
            .accountSid(ACCOUNT_SID)
            .httpClient(http)
            .build();
    }

    private static Send taskReturning(TwilioRestClient client) {
        Send task = Send.builder()
            .accountSID(Property.ofValue(ACCOUNT_SID))
            .authToken(Property.ofValue("test_auth_token"))
            .from(Property.ofValue("+15005550006"))
            .to(Property.ofValue("+15555550100"))
            .body(Property.ofValue("test"))
            .build();

        Send spied = spy(task);
        doReturn(client).when(spied).restClient(anyString(), anyString());

        return spied;
    }

    @Test
    void sendSms() throws Exception {
        var http = mock(com.twilio.http.HttpClient.class);
        when(http.reliableRequest(any())).thenReturn(
            new Response("{\"sid\":\"SM1234567890abcdef\",\"status\":\"queued\"}", 201)
        );
        var client = new TwilioRestClient.Builder(ACCOUNT_SID, "test_auth_token")
            .accountSid(ACCOUNT_SID).httpClient(http).build();

        var output = taskReturning(client).run(runContextFactory.of(Map.of()));

        var sent = ArgumentCaptor.forClass(Request.class);
        verify(http).reliableRequest(sent.capture());
        assertThat(sent.getValue().getPostParams().get("To"), hasItem("+15555550100"));
        assertThat(sent.getValue().getPostParams().get("Body"), hasItem("test"));

        assertThat(output.getSid(), is("SM1234567890abcdef"));
        assertThat(output.getStatus(), is("queued"));
    }

    @Test
    void failsOnNon201() {
        var task = taskReturning(clientReturning(
            "{\"code\":21211,\"message\":\"The 'To' number is not a valid phone number.\",\"status\":400}", 400
        ));

        var exception = assertThrows(RuntimeException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("not a valid phone number"));
        assertThat(exception.getMessage(), not(containsString("[B@")));
    }
}
