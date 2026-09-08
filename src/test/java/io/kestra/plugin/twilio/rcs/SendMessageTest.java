package io.kestra.plugin.twilio.rcs;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
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
    private static final String ACCOUNT_SID = "AC00000000000000000000000000000000";
    private static final String MESSAGING_SERVICE_SID = "MG00000000000000000000000000000000";

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

        SendMessage task = task(wireMock)
            .body(Property.ofValue("Hello from Kestra."))
            .build();

        SendMessage.Output output = task.run(runContextFactory.of(Map.of()));

        assertThat(output.getSid(), is("SM1234567890abcdef"));
        assertThat(output.getStatus(), is("queued"));

        verify(
            postRequestedFor(urlPathMatching(MESSAGES_PATH))
                .withRequestBody(containing("MessagingServiceSid=" + MESSAGING_SERVICE_SID))
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

        SendMessage task = task(wireMock)
            .contentSid(Property.ofValue("HX00000000000000000000000000000000"))
            .build();

        SendMessage.Output output = task.run(runContextFactory.of(Map.of()));

        assertThat(output.getSid(), is("SMcontent0000000000"));
        assertThat(output.getStatus(), is("accepted"));

        verify(
            postRequestedFor(urlPathMatching(MESSAGES_PATH))
                .withRequestBody(containing("ContentSid=HX00000000000000000000000000000000"))
                .withRequestBody(notMatching(".*Body=.*"))
        );
    }

    /**
     * Twilio decides RCS versus SMS server side, so the contract this pins is that the task sends no
     * fallback configuration of its own and accepts a response describing an SMS-delivered message.
     */
    @Test
    void requestsNoFallbackConfigurationAndAcceptsSmsResponse(WireMockRuntimeInfo wireMock) throws Exception {
        stubMessagesApi(201, """
            {
              "sid": "SMfallback000000000",
              "status": "queued",
              "num_segments": "1",
              "messaging_service_sid": "MG00000000000000000000000000000000"
            }
            """);

        SendMessage task = task(wireMock)
            .body(Property.ofValue("Delivered either way."))
            .build();

        SendMessage.Output output = task.run(runContextFactory.of(Map.of()));

        assertThat(output.getSid(), is("SMfallback000000000"));
        assertThat(output.getStatus(), is("queued"));

        verify(
            postRequestedFor(urlPathMatching(MESSAGES_PATH))
                .withRequestBody(notMatching(".*(Fallback|SmsFallback|Channel|ContentRetention).*"))
        );
    }

    @Test
    void failsOnNon201(WireMockRuntimeInfo wireMock) {
        stubMessagesApi(400, """
            {"code":21211,"message":"The 'To' number is not a valid phone number.","status":400}
            """);

        SendMessage task = task(wireMock)
            .to(Property.ofValue("invalid"))
            .body(Property.ofValue("test"))
            .build();

        assertThrows(RuntimeException.class, () -> task.run(runContextFactory.of(Map.of())));
    }

    @Test
    void failsOnEmptyResponseBody(WireMockRuntimeInfo wireMock) {
        stubMessagesApi(201, "");

        SendMessage task = task(wireMock)
            .body(Property.ofValue("test"))
            .build();

        var exception = assertThrows(RuntimeException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("empty body"));
    }

    @Test
    void failsOnUnparseableResponseBody(WireMockRuntimeInfo wireMock) {
        stubMessagesApi(201, "not json");

        SendMessage task = task(wireMock)
            .body(Property.ofValue("test"))
            .build();

        var exception = assertThrows(RuntimeException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("unparseable"));
    }

    @Test
    void failsOnInvalidAccountSid(WireMockRuntimeInfo wireMock) {
        SendMessage task = task(wireMock)
            .accountSID(Property.ofValue("not-an-account-sid"))
            .body(Property.ofValue("test"))
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of(Map.of())));
    }

    @Test
    void failsWithoutBodyOrContentSid(WireMockRuntimeInfo wireMock) {
        SendMessage task = task(wireMock).build();

        var exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("either body or contentSid"));
    }

    @Test
    void failsWithoutSender(WireMockRuntimeInfo wireMock) {
        SendMessage task = TestSendMessage.builder()
            .base(wireMock.getHttpBaseUrl())
            .accountSID(Property.ofValue(ACCOUNT_SID))
            .authToken(Property.ofValue("test_auth_token"))
            .to(Property.ofValue("+15555550100"))
            .body(Property.ofValue("test"))
            .build();

        var exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("either from or messagingServiceSid"));
    }

    @Test
    void failsWhenBothSendersSet(WireMockRuntimeInfo wireMock) {
        SendMessage task = task(wireMock)
            .from(Property.ofValue("+15005550006"))
            .body(Property.ofValue("test"))
            .build();

        var exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("mutually exclusive"));
    }

    @Test
    void failsWhenMessagingServiceSidPassedAsFrom(WireMockRuntimeInfo wireMock) {
        SendMessage task = TestSendMessage.builder()
            .base(wireMock.getHttpBaseUrl())
            .accountSID(Property.ofValue(ACCOUNT_SID))
            .authToken(Property.ofValue("test_auth_token"))
            .from(Property.ofValue(MESSAGING_SERVICE_SID))
            .to(Property.ofValue("+15555550100"))
            .body(Property.ofValue("test"))
            .build();

        var exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of(Map.of())));
        assertThat(exception.getMessage(), containsString("messagingServiceSid instead"));
    }

    private static TestSendMessage.TestSendMessageBuilder<?, ?> task(WireMockRuntimeInfo wireMock) {
        return TestSendMessage.builder()
            .base(wireMock.getHttpBaseUrl())
            .accountSID(Property.ofValue(ACCOUNT_SID))
            .authToken(Property.ofValue("test_auth_token"))
            .messagingServiceSid(Property.ofValue(MESSAGING_SERVICE_SID))
            .to(Property.ofValue("+15555550100"));
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
