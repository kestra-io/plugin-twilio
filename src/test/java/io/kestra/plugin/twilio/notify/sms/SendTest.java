package io.kestra.plugin.twilio.notify.sms;

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
    void sendSms(WireMockRuntimeInfo wireMock) throws Exception {
        stubFor(
            post(urlPathMatching("/2010-04-01/Accounts/.*/Messages.json"))
                .willReturn(aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "sid": "SM1234567890abcdef",
                          "status": "queued",
                          "from": "+15005550006",
                          "to": "+15555550100",
                          "body": "Hello from Kestra."
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
            .body(Property.ofValue("Hello from Kestra."))
            .build();

        Send.Output output = task.run(runContext);

        assertThat(output.getSid(), is("SM1234567890abcdef"));
        assertThat(output.getStatus(), is("queued"));

        verify(postRequestedFor(urlPathMatching("/2010-04-01/Accounts/.*/Messages.json"))
            .withRequestBody(containing("From=%2B15005550006"))
            .withRequestBody(containing("To=%2B15555550100"))
            .withRequestBody(containing("Body=Hello+from+Kestra.")));
    }

    @Test
    void failsOnNon201(WireMockRuntimeInfo wireMock) throws Exception {
        stubFor(
            post(urlPathMatching("/2010-04-01/Accounts/.*/Messages.json"))
                .willReturn(aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {"code":21211,"message":"The 'To' number is not a valid phone number.","status":400}
                        """))
        );

        RunContext runContext = runContextFactory.of(Map.of());

        Send task = TestSend.builder()
            .base(wireMock.getHttpBaseUrl())
            .accountSID(Property.ofValue("AC00000000000000000000000000000000"))
            .authToken(Property.ofValue("test_auth_token"))
            .from(Property.ofValue("+15005550006"))
            .to(Property.ofValue("invalid"))
            .body(Property.ofValue("test"))
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
