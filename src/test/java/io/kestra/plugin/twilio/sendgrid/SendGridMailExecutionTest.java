package io.kestra.plugin.twilio.sendgrid;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.TestRunner;
import io.kestra.plugin.twilio.AbstractTwilioTest;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KestraTest
class SendGridMailExecutionTest extends AbstractTwilioTest {
    @Inject
    protected TestRunner runner;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @BeforeAll
    void init() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(SendGridMailExecutionTest.class.getClassLoader().getResource("flows/common")));
        repositoryLoader.load(Objects.requireNonNull(SendGridMailExecutionTest.class.getClassLoader().getResource("flows/sendgrid")));
        this.runner.run();
    }

    @Test
    void testFlow() throws Exception {
        var execution = runAndCaptureExecution(
            "main-flow-that-fails",
            "sendgrid"
        );

        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent(), is(State.Type.FAILED));
    }
}
