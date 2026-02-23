package io.kestra.plugin.twilio.segment.reverseetl;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.twilio.segment.AbstractSegmentConnection;
import io.kestra.plugin.twilio.segment.reverseetl.models.ReverseEtlSyncStatus;
import io.kestra.plugin.twilio.segment.reverseetl.models.ReverseEtlSyncStatusResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.IOException;

@SuperBuilder
@Getter
@NoArgsConstructor
@Schema(
    title = "Fetch Segment Reverse ETL sync status",
    description = "Retrieves the latest status for a Reverse ETL sync within a model using the Segment Public API."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = """
                id: get_sync_status
                namespace: company.team

                tasks:
                  - id: get_sync_status
                    type: io.kestra.plugin.twilio.segment.reverseetl.Status
                    token: "{{ secret('SEGMENT_TOKEN') }}"
                    modelId: model_id
                    syncId: sync_id
                """
        )
    },
    aliases = "io.kestra.plugin.segment.reverseetl.Status"
)
public class Status extends AbstractSegmentConnection implements RunnableTask<Status.Output> {
    @Schema(
        title = "Reverse ETL model ID",
        description = "Segment model identifier that owns the sync"
    )
    @NotNull
    private Property<String> modelId;

    @Schema(
        title = "Reverse ETL sync ID",
        description = "Sync identifier returned by the trigger operation"
    )
    @NotNull
    private Property<String> syncId;

    @Override
    public Output run(RunContext runContext) throws IllegalVariableEvaluationException, HttpClientException, IOException {
        ReverseEtlSyncStatusResponse response = request(runContext,
            "GET",
            "/reverse-etl-models/"
                + runContext.render(modelId).as(String.class).orElseThrow()
                + "/syncs/"
                + runContext.render(syncId).as(String.class).orElseThrow(),
            null,
            ReverseEtlSyncStatusResponse.class
        ).getBody();

        return Output.builder()
            .status(response.getData().getReverseETLSyncStatus())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Sync status response",
            description = "Full status payload returned by Segment"
        )
        private final ReverseEtlSyncStatus status;
    }
}
