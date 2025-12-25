package io.kestra.plugin.twilio.notify;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.TestRunner;
import io.kestra.plugin.twilio.AbstractTwilioTest;
import io.kestra.plugin.twilio.FakeWebhookController;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@KestraTest
public class TwilioExecutionTest extends AbstractTwilioTest {

    @Inject
    protected TestRunner runner;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(TwilioExecutionTest.class.getClassLoader().getResource("flows/common")));
        repositoryLoader.load(Objects.requireNonNull(TwilioExecutionTest.class.getClassLoader().getResource("flows/notify")));
        this.runner.run();
    }

    @Test
    void flow() throws Exception {
        var failedExecution = runAndCaptureExecution(
            "main-flow-that-fails",
            "twilio"
        );

        String receivedData = waitForWebhookData(() -> FakeWebhookController.data,5000);

        String decodedData = URLDecoder.decode(receivedData, StandardCharsets.UTF_8);

        assertThat(decodedData, containsString(failedExecution.getId()));
        assertThat(decodedData, containsString("Failed on task `failed`"));
        assertThat(decodedData, containsString("Final task ID failed"));
    }
}
