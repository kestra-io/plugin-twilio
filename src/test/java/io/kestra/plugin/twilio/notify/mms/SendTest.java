package io.kestra.plugin.twilio.notify.mms;

import java.util.List;
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

    /** A real client with only the transport stubbed, so the SDK still builds and parses everything itself. */
    private static com.twilio.http.HttpClient transportReturning(String body, int status) {
        var http = mock(com.twilio.http.HttpClient.class);
        when(http.reliableRequest(any())).thenReturn(new Response(body, status));

        return http;
    }

    private static Send taskOn(com.twilio.http.HttpClient http, List<String> mediaUrls, String body) {
        var client = new TwilioRestClient.Builder(ACCOUNT_SID, "test_auth_token")
            .accountSid(ACCOUNT_SID)
            .httpClient(http)
            .build();

        Send task = Send.builder()
            .accountSID(Property.ofValue(ACCOUNT_SID))
            .authToken(Property.ofValue("test_auth_token"))
            .from(Property.ofValue("+15005550006"))
            .to(Property.ofValue("+15555550100"))
            .body(Property.ofValue(body))
            .mediaUrls(Property.ofValue(mediaUrls))
            .build();

        Send spied = spy(task);
        doReturn(client).when(spied).restClient(anyString(), anyString());

        return spied;
    }

    @Test
    void sendMms() throws Exception {
        var http = transportReturning("{\"sid\":\"MM1234567890abcdef\",\"status\":\"queued\"}", 201);

        var output = taskOn(http, List.of("https://example.com/report.png"), "Here is your report.").run(
            runContextFactory.of(Map.of())
        );

        assertThat(output.getSid(), is("MM1234567890abcdef"));
        assertThat(output.getStatus(), is("queued"));

        var sent = ArgumentCaptor.forClass(Request.class);
        verify(http).reliableRequest(sent.capture());
        var params = sent.getValue().getPostParams();

        assertThat(params, hasKey("From"));
        assertThat(params, hasKey("To"));
        assertThat(params, hasKey("Body"));
        // a single value, not one bracketed list
        assertThat(params.get("MediaUrl"), contains(equalTo("https://example.com/report.png")));
    }

    @Test
    void sendMmsMultipleMediaUrls() throws Exception {
        var http = transportReturning("{\"sid\":\"MM_multi_media\",\"status\":\"queued\"}", 201);

        var output = taskOn(
            http,
            List.of("https://example.com/image1.png", "https://example.com/image2.png"),
            "Two attachments."
        ).run(runContextFactory.of(Map.of()));

        assertThat(output.getSid(), is("MM_multi_media"));

        var sent = ArgumentCaptor.forClass(Request.class);
        verify(http).reliableRequest(sent.capture());

        // each URL is its own repeated MediaUrl param
        assertThat(
            sent.getValue().getPostParams().get("MediaUrl"),
            contains(equalTo("https://example.com/image1.png"), equalTo("https://example.com/image2.png"))
        );
    }

    @Test
    void failsOnEmptyMediaUrls() {
        var task = taskOn(transportReturning("{}", 201), List.of(), "no media");

        var exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("at least one URL"));
    }

    @Test
    void failsOnNon201() {
        var task = taskOn(
            transportReturning("{\"code\":21620,\"message\":\"Invalid media URL\",\"status\":400}", 400),
            List.of("https://example.com/report.png"),
            "bad"
        );

        var exception = assertThrows(RuntimeException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("Invalid media URL"));
        assertThat(exception.getMessage(), not(containsString("[B@")));
    }
}
