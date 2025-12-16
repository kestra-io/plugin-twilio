package io.kestra.plugin.segment.reverseetl;

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
class StatusIT {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void shouldGetSyncStatus() throws Exception {
        Status task = Status.builder()
            .token(Property.ofValue(System.getenv("SEGMENT_TOKEN")))
            .modelId(Property.ofValue("model-id"))
            .syncId(Property.ofValue("sync-id"))
            .build();

        RunContext runContext = runContextFactory.of();

        Status.Output output = task.run(runContext);

        assertThat(output.getStatus()).isNotNull();
    }
}
