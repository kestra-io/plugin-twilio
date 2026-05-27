package io.kestra.plugin.twilio.notify.mms;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;

import jakarta.inject.Inject;
import lombok.experimental.SuperBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@WireMockTest
class SendTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void sendMms(WireMockRuntimeInfo wireMock) throws Exception {
        stubFor(
            post(urlPathMatching("/2010-04-01/Accounts/.*/Messages.json"))
                .willReturn(aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "sid": "MM1234567890abcdef",
                          "status": "queued",
                          "from": "+15005550006",
                          "to": "+15555550100",
                          "body": "Here is your report.",
                          "num_media": "1"
                        }
                        """))
        );

        RunContext runContext = runContextFactory.of(Map.of());

        Send task = TestSend.builder()
            .base(wireMock.getHttpBaseUrl())
            .accountSID(Property.ofValue("AC00000000000000000000000000000000"))
            .authToken(Property.ofValue("test_auth_token"))
            .from(Property.ofValue("+15005550006"))
            .to(Property.ofValue("+15555550100"))
            .body(Property.ofValue("Here is your report."))
            .mediaUrls(Property.ofValue(List.of("https://example.com/report.png")))
            .build();

        Send.Output output = task.run(runContext);

        assertThat(output.getSid(), is("MM1234567890abcdef"));
        assertThat(output.getStatus(), is("queued"));

        // single encoded value, not a bracketed list
        verify(postRequestedFor(urlPathMatching("/2010-04-01/Accounts/.*/Messages.json"))
            .withRequestBody(containing("From="))
            .withRequestBody(containing("To="))
            .withRequestBody(containing("Body="))
            .withRequestBody(containing("MediaUrl=https%3A%2F%2Fexample.com%2Freport.png"))
            .withRequestBody(notMatching(".*MediaUrl=%5B.*")));
    }

    @Test
    void sendMmsMultipleMediaUrls(WireMockRuntimeInfo wireMock) throws Exception {
        stubFor(
            post(urlPathMatching("/2010-04-01/Accounts/.*/Messages.json"))
                .willReturn(aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "sid": "MM_multi_media",
                          "status": "queued",
                          "num_media": "2"
                        }
                        """))
        );

        RunContext runContext = runContextFactory.of(Map.of());

        Send task = TestSend.builder()
            .base(wireMock.getHttpBaseUrl())
            .accountSID(Property.ofValue("AC00000000000000000000000000000000"))
            .authToken(Property.ofValue("test_auth_token"))
            .from(Property.ofValue("+15005550006"))
            .to(Property.ofValue("+15555550100"))
            .body(Property.ofValue("Two attachments."))
            .mediaUrls(Property.ofValue(List.of(
                "https://example.com/image1.png",
                "https://example.com/image2.png"
            )))
            .build();

        Send.Output output = task.run(runContext);

        assertThat(output.getSid(), is("MM_multi_media"));
        assertThat(output.getStatus(), is("queued"));

        // each URL is its own repeated MediaUrl param
        verify(postRequestedFor(urlPathMatching("/2010-04-01/Accounts/.*/Messages.json"))
            .withRequestBody(containing("MediaUrl=https%3A%2F%2Fexample.com%2Fimage1.png"))
            .withRequestBody(containing("MediaUrl=https%3A%2F%2Fexample.com%2Fimage2.png")));
    }

    @Test
    void failsOnEmptyMediaUrls() {
        RunContext runContext = runContextFactory.of(Map.of());

        Send task = Send.builder()
            .accountSID(Property.ofValue("AC00000000000000000000000000000000"))
            .authToken(Property.ofValue("test_auth_token"))
            .from(Property.ofValue("+15005550006"))
            .to(Property.ofValue("+15555550100"))
            .body(Property.ofValue("no media"))
            .mediaUrls(Property.ofValue(List.of()))
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void failsOnNon201(WireMockRuntimeInfo wireMock) throws Exception {
        stubFor(
            post(urlPathMatching("/2010-04-01/Accounts/.*/Messages.json"))
                .willReturn(aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {"code":21610,"message":"Attempt to send to unsubscribed recipient.","status":400}
                        """))
        );

        RunContext runContext = runContextFactory.of(Map.of());

        Send task = TestSend.builder()
            .base(wireMock.getHttpBaseUrl())
            .accountSID(Property.ofValue("AC00000000000000000000000000000000"))
            .authToken(Property.ofValue("test_auth_token"))
            .from(Property.ofValue("+15005550006"))
            .to(Property.ofValue("+15555550100"))
            .body(Property.ofValue("test"))
            .mediaUrls(Property.ofValue(List.of("https://example.com/img.png")))
            .build();

        assertThrows(RuntimeException.class, () -> task.run(runContext));
    }

    @SuperBuilder
    static class TestSend extends Send {
        private final String base;

        TestSend(String base) {
            this.base = base;
        }

        @Override
        protected String baseUrl() {
            return base;
        }
    }
}
