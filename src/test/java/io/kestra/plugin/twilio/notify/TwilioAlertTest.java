package io.kestra.plugin.twilio.notify;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.google.common.io.Files;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.twilio.FakeWebhookController;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@KestraTest
public class TwilioAlertTest {

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private ModelValidator modelValidator;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of(
            Map.of(
                "body", "Twilio test alert",
                "identity", "SomeUser"
            )
        );

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        TwilioAlert task = TwilioAlert.builder()
            .url(embeddedServer.getURI() + "/webhook-unit-test")
            .payload(
                Property.ofValue(
                    Files.asCharSource(
                        new File(
                            Objects.requireNonNull(
                                TwilioAlertTest.class.getClassLoader()
                                    .getResource("twilio.peb")
                            )
                                .toURI()
                        ),
                        StandardCharsets.UTF_8
                    ).read()
                )
            )
            .accountSID(IdUtils.create())
            .authToken(UUID.randomUUID().toString())
            .build();

        task.run(runContext);

        assertThat(FakeWebhookController.data, containsString("someUserTag"));
    }

    @SuppressWarnings("deprecation")
    @Test
    void flowSettingDeprecatedOptionsStillValidatesAndRuns() throws Exception {
        var message = "deprecated-options-" + IdUtils.create();
        var embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        var flow = YamlParser.parse(
            """
                id: twilio_deprecated_options
                namespace: io.kestra.tests
                tasks:
                  - id: alert
                    type: io.kestra.plugin.twilio.notify.TwilioAlert
                    url: "%s/webhook-unit-test"
                    accountSID: "%s"
                    authToken: "%s"
                    payload: '{"identity": "SomeUser", "body": "%s"}'
                    options:
                      readTimeout: PT30S
                      connectionPoolIdleTimeout: PT10S
                      maxContentLength: 1024
                """.formatted(embeddedServer.getURI(), IdUtils.create(), UUID.randomUUID(), message),
            Flow.class
        );

        assertThat(modelValidator.isValid(flow).isEmpty(), is(true));

        var runContext = runContextFactory.of(Map.of());
        var task = (TwilioAlert) flow.getTasks().getFirst();
        var options = task.getOptions();
        assertThat(runContext.render(options.getReadTimeout()).as(Duration.class).orElseThrow(), is(Duration.ofSeconds(30)));
        assertThat(runContext.render(options.getConnectionPoolIdleTimeout()).as(Duration.class).orElseThrow(), is(Duration.ofSeconds(10)));
        assertThat(runContext.render(options.getMaxContentLength()).as(Integer.class).orElseThrow(), is(1024));

        task.run(runContext);

        assertThat(FakeWebhookController.data, containsString(message));
    }

}
