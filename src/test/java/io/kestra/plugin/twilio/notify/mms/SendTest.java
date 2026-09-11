package io.kestra.plugin.twilio.notify.mms;

import java.util.List;
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

    private static Send task(List<String> mediaUrls, String body) {
        return Send.builder()
            .accountSID(Property.ofValue(ACCOUNT_SID))
            .authToken(Property.ofValue(AUTH_TOKEN))
            .from(Property.ofValue("+15005550006"))
            .to(Property.ofValue("+15555550100"))
            .body(Property.ofValue(body))
            .mediaUrls(Property.ofValue(mediaUrls))
            .build();
    }

    @Test
    void sendMms() throws Exception {
        var http = respondsWith(201, """
            {"sid":"MM1234567890abcdef","status":"queued"}
            """);

        var output = sending(task(List.of("https://example.com/report.png"), "Here is your report."), http)
            .run(runContextFactory.of(Map.of()));

        assertThat(output.getSid(), is("MM1234567890abcdef"));
        assertThat(output.getStatus(), is("queued"));

        var params = sentParams(http);
        assertThat(params, hasKey("From"));
        assertThat(params, hasKey("To"));
        assertThat(params, hasKey("Body"));
        // a single value, not one bracketed list
        assertThat(params.get("MediaUrl"), contains(equalTo("https://example.com/report.png")));
    }

    @Test
    void sendMmsMultipleMediaUrls() throws Exception {
        var http = respondsWith(201, """
            {"sid":"MM_multi_media","status":"queued"}
            """);

        var output = sending(
            task(List.of("https://example.com/image1.png", "https://example.com/image2.png"), "Two attachments."),
            http
        ).run(runContextFactory.of(Map.of()));

        assertThat(output.getSid(), is("MM_multi_media"));

        // each URL is its own repeated MediaUrl param
        assertThat(
            sentParams(http).get("MediaUrl"),
            contains(equalTo("https://example.com/image1.png"), equalTo("https://example.com/image2.png"))
        );
    }

    @Test
    void failsOnEmptyMediaUrls() {
        var task = sending(task(List.of(), "no media"), respondsWith(201, "{}"));

        var exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("at least one URL"));
    }

    @Test
    void failsOnNon201() {
        var task = sending(
            task(List.of("https://example.com/report.png"), "bad"),
            respondsWith(400, """
                {"code":21620,"message":"Invalid media URL","status":400}
                """)
        );

        var exception = assertThrows(RuntimeException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("Invalid media URL"));
        assertThat(exception.getMessage(), not(containsString("[B@")));
    }
}
