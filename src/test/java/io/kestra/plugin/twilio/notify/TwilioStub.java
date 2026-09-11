package io.kestra.plugin.twilio.notify;

import java.util.List;
import java.util.Map;

import org.mockito.ArgumentCaptor;

import com.twilio.http.HttpClient;
import com.twilio.http.Request;
import com.twilio.http.Response;
import com.twilio.http.TwilioRestClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Stubs only the SDK's transport, so the client it wraps still builds the request and parses the response itself
 * and the tests assert on what Twilio would actually have received.
 */
public final class TwilioStub {
    public static final String ACCOUNT_SID = "AC00000000000000000000000000000000";
    public static final String AUTH_TOKEN = "test_auth_token";

    private TwilioStub() {
    }

    public static HttpClient respondsWith(int status, String body) {
        var http = mock(HttpClient.class);
        when(http.reliableRequest(any())).thenReturn(new Response(body, status));

        return http;
    }

    /** Returns a spy of the task that talks to the stubbed transport instead of Twilio. */
    public static <T extends AbstractMessageSend> T sending(T task, HttpClient http) {
        var client = new TwilioRestClient.Builder(ACCOUNT_SID, AUTH_TOKEN)
            .accountSid(ACCOUNT_SID)
            .httpClient(http)
            .build();

        T spied = spy(task);
        doReturn(client).when(spied).restClient(anyString(), anyString());

        return spied;
    }

    public static Map<String, List<String>> sentParams(HttpClient http) {
        var sent = ArgumentCaptor.forClass(Request.class);
        verify(http).reliableRequest(sent.capture());

        return sent.getValue().getPostParams();
    }
}
