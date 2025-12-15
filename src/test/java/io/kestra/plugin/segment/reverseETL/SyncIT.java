package io.kestra.plugin.segment.reverseETL;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@KestraTest
@EnabledIfEnvironmentVariable(named = "SEGMENT_TOKEN", matches = ".+")
class SyncIT {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void shouldTriggerReverseEtlSync() throws Exception {
        Sync task = Sync.builder()
            .token(Property.ofValue(System.getenv("SEGMENT_TOKEN")))
            .sourceId(Property.ofValue("source-id"))
            .modelId(Property.ofValue("model-id"))
            .subscriptionId(Property.ofValue("subscription-id"))
            .wait(Property.ofValue(false))
            .build();

        RunContext runContext = runContextFactory.of();

        Sync.Output output = task.run(runContext);

        assertThat(output.getSyncId()).isNotNull();
    }
}
