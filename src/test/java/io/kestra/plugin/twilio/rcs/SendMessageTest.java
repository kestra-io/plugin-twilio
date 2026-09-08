package io.kestra.plugin.twilio.rcs;

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
class SendMessageTest {

    private static final String MESSAGES_PATH = "/2010-04-01/Accounts/.*/Messages.json";

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void sendRcs(WireMockRuntimeInfo wireMock) throws Exception {
        stubMessagesApi(201, """
            {
              "sid": "SM1234567890abcdef",
              "status": "queued",
              "messaging_service_sid": "MG00000000000000000000000000000000",
              "to": "+15555550100",
              "body": "Hello from Kestra."
            }
            """);

        RunContext runContext = runContextFactory.of(Map.of());

        SendMessage task = TestSendMessage.builder()
            .base(wireMock.getHttpBaseUrl())
            .accountSID(Property.ofValue("AC00000000000000000000000000000000"))
            .authToken(Property.ofValue("test_auth_token"))
            .from(Property.ofValue("MG00000000000000000000000000000000"))
            .to(Property.ofValue("+15555550100"))
            .body(Property.ofValue("Hello from Kestra."))
            .build();

        SendMessage.Output output = task.run(runContext);

        assertThat(output.getMessageSid(), is("SM1234567890abcdef"));
        assertThat(output.getStatus(), is("queued"));

        verify(
            postRequestedFor(urlPathMatching(MESSAGES_PATH))
                .withRequestBody(containing("From=MG00000000000000000000000000000000"))
                .withRequestBody(containing("To=%2B15555550100"))
                .withRequestBody(containing("Body=Hello+from+Kestra."))
                .withRequestBody(notMatching(".*ContentSid.*"))
        );
    }

    @Test
    void sendRcsWithContentTemplate(WireMockRuntimeInfo wireMock) throws Exception {
        stubMessagesApi(201, """
            {"sid": "SMcontent0000000000", "status": "accepted"}
            """);

        RunContext runContext = runContextFactory.of(Map.of());

        SendMessage task = TestSendMessage.builder()
            .base(wireMock.getHttpBaseUrl())
            .accountSID(Property.ofValue("AC00000000000000000000000000000000"))
            .authToken(Property.ofValue("test_auth_token"))
            .from(Property.ofValue("MG00000000000000000000000000000000"))
            .to(Property.ofValue("+15555550100"))
            .body(Property.ofValue("Your order has been shipped."))
            .contentSid(Property.ofValue("HX00000000000000000000000000000000"))
            .build();

        SendMessage.Output output = task.run(runContext);

        assertThat(output.getMessageSid(), is("SMcontent0000000000"));
        assertThat(output.getStatus(), is("accepted"));

        verify(
            postRequestedFor(urlPathMatching(MESSAGES_PATH))
                .withRequestBody(containing("ContentSid=HX00000000000000000000000000000000"))
        );
    }

    /**
     * Twilio performs the RCS-to-SMS fallback server side: the task sees a normal 201 whose payload
     * reports the message was delivered over SMS. Nothing extra should be required of the caller.
     */
    @Test
    void fallsBackToSmsTransparently(WireMockRuntimeInfo wireMock) throws Exception {
        stubMessagesApi(201, """
            {
              "sid": "SMfallback000000000",
              "status": "queued",
              "num_segments": "1",
              "messaging_service_sid": "MG00000000000000000000000000000000"
            }
            """);

        RunContext runContext = runContextFactory.of(Map.of());

        SendMessage task = TestSendMessage.builder()
            .base(wireMock.getHttpBaseUrl())
            .accountSID(Property.ofValue("AC00000000000000000000000000000000"))
            .authToken(Property.ofValue("test_auth_token"))
            .from(Property.ofValue("MG00000000000000000000000000000000"))
            .to(Property.ofValue("+15555550100"))
            .body(Property.ofValue("Fallback body."))
            .build();

        SendMessage.Output output = task.run(runContext);

        assertThat(output.getMessageSid(), is("SMfallback000000000"));
        assertThat(output.getStatus(), is("queued"));
    }

    @Test
    void failsOnNon201(WireMockRuntimeInfo wireMock) {
        stubMessagesApi(400, """
            {"code":21211,"message":"The 'To' number is not a valid phone number.","status":400}
            """);

        RunContext runContext = runContextFactory.of(Map.of());

        SendMessage task = TestSendMessage.builder()
            .base(wireMock.getHttpBaseUrl())
            .accountSID(Property.ofValue("AC00000000000000000000000000000000"))
            .authToken(Property.ofValue("test_auth_token"))
            .from(Property.ofValue("MG00000000000000000000000000000000"))
            .to(Property.ofValue("invalid"))
            .body(Property.ofValue("test"))
            .build();

        assertThrows(RuntimeException.class, () -> task.run(runContext));
    }

    @Test
    void failsOnInvalidAccountSid(WireMockRuntimeInfo wireMock) {
        RunContext runContext = runContextFactory.of(Map.of());

        SendMessage task = TestSendMessage.builder()
            .base(wireMock.getHttpBaseUrl())
            .accountSID(Property.ofValue("not-an-account-sid"))
            .authToken(Property.ofValue("test_auth_token"))
            .from(Property.ofValue("MG00000000000000000000000000000000"))
            .to(Property.ofValue("+15555550100"))
            .body(Property.ofValue("test"))
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    private static void stubMessagesApi(int status, String body) {
        stubFor(
            post(urlPathMatching(MESSAGES_PATH))
                .willReturn(
                    aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                )
        );
    }

    @SuperBuilder
    static class TestSendMessage extends SendMessage {
        private final String base;

        TestSendMessage(String base) {
            this.base = base;
        }

        @Override
        protected String baseUrl() {
            return base;
        }
    }
}
