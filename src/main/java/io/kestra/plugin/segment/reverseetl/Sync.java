package io.kestra.plugin.segment.reverseetl;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.*;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.Await;
import io.kestra.plugin.segment.AbstractSegmentConnection;
import io.kestra.plugin.segment.reverseetl.models.ReverseEtlSyncRequest;
import io.kestra.plugin.segment.reverseetl.models.ReverseEtlSyncResponse;
import io.kestra.plugin.segment.reverseetl.models.ReverseEtlSyncStatus;
import io.kestra.plugin.segment.reverseetl.models.ReverseEtlSyncStatusResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@SuperBuilder
@Getter
@NoArgsConstructor
@Schema(title = "Trigger a Segment Reverse ETL sync")
@Plugin(
    examples = {
        @Example(
            full = true,
            code = """
                id: run_segment_sync
                namespace: company.team

                tasks:
                  - id: segment_reverse_etl
                    type: io.kestra.plugin.segment.reverseetl.Sync
                    token: "{{ secret('SEGMENT_TOKEN') }}"
                    sourceId: warehouse_id
                    modelId: model_id
                    subscriptionId: subscription_id
                    wait: true
                """
        )
    }
)
public class Sync extends AbstractSegmentConnection implements RunnableTask<Sync.Output> {

    @NotNull
    @Schema(
        title = "Warehouse source ID",
        description = "The Segment warehouse source ID backing the Reverse ETL sync"
    )
    private Property<String> sourceId;

    @NotNull
    @Schema(
        title = "Reverse ETL model ID",
        description = "The Segment Reverse ETL model ID to execute"
    )
    private Property<String> modelId;

    @NotNull
    @Schema(
        title = "Subscription ID",
        description = "The Segment Reverse ETL subscription (mapping) ID"
    )
    private Property<String> subscriptionId;

    @Builder.Default
    @Schema(
        title = "Wait for completion",
        description = "Whether to wait for the Reverse ETL sync to complete before finishing the task"
    )
    private Property<Boolean> wait = Property.ofValue(false);

    @Builder.Default
    @Schema(
        title = "Maximum wait duration",
        description = "Maximum total time to wait for the Reverse ETL sync to complete"
    )
    private Property<Duration> maxDuration = Property.ofValue(Duration.ofHours(1));

    @Schema(
        title = "Polling interval",
        description = "How often to poll Segment for sync status"
    )
    @Builder.Default
    private Property<Duration> pollInterval = Property.ofValue(Duration.ofSeconds(5));

    @Builder.Default
    @Schema(
        title = "Fail on sync error",
        description = "Whether the task should fail if the Reverse ETL sync finishes with a failure status"
    )
    private Property<Boolean> errorOnFailing = Property.ofValue(false);

    @Override
    public Output run(RunContext runContext) throws Exception {
        ReverseEtlSyncResponse response = request(
            runContext,
            "POST",
            "/reverse-etl-syncs",
            ReverseEtlSyncRequest.builder()
                .sourceId(runContext.render(sourceId).as(String.class).orElseThrow())
                .modelId(runContext.render(modelId).as(String.class).orElseThrow())
                .subscriptionId(runContext.render(subscriptionId).as(String.class).orElseThrow())
                .build(),
            ReverseEtlSyncResponse.class
        ).getBody();

        String syncId = response.getData().getReverseETLManualSync().getSyncId();

        runContext.logger().info("Triggered Segment Reverse ETL sync with id={}", syncId);

        if (!runContext.render(wait).as(Boolean.class).orElse(false)) {
            return Output.builder().syncId(syncId).created(response.getData().getReverseETLManualSync()).build();
        }

        ReverseEtlSyncStatus status = waitForCompletion(runContext, syncId);

        runContext.logger().info("Segment Reverse ETL sync {} finished with status={}", syncId, status.getStatus());

        if (runContext.render(errorOnFailing).as(Boolean.class).orElse(false) && !status.isSuccessful()) {
            throw new RuntimeException("Segment Reverse ETL failed with status: " + status.getStatus());
        }

        return Output.builder()
            .syncId(syncId)
            .status(status)
            .build();
    }

    private ReverseEtlSyncStatus waitForCompletion(RunContext runContext, String syncId) throws IllegalVariableEvaluationException, TimeoutException {
        ReverseEtlSyncStatusResponse response = Await.until(
            () -> {
                ReverseEtlSyncStatusResponse statusResponse = getStatus(runContext, modelId, syncId);

                if (statusResponse == null || statusResponse.getData() == null) {
                    return null;
                }

                ReverseEtlSyncStatus status = statusResponse.getData().getReverseETLSyncStatus();

                return status != null && status.isTerminal() ? statusResponse : null;
            },
            runContext.render(pollInterval).as(Duration.class).orElse(Duration.ofSeconds(5)),
            runContext.render(maxDuration).as(Duration.class).orElse(Duration.ofHours(1))
        );

        if (response == null || response.getData() == null) {
            throw new IllegalStateException("Segment returned an invalid Reverse ETL status response");
        }

        return response.getData().getReverseETLSyncStatus();
    }

    private ReverseEtlSyncStatusResponse getStatus(RunContext runContext, @NotNull Property<String> modelId, String syncId) {
        try {
            return request(runContext,
                "GET",
                "/reverse-etl-models/"
                    + runContext.render(modelId).as(String.class).orElseThrow()
                    + "/syncs/" + syncId,
                null,
                ReverseEtlSyncStatusResponse.class
            ).getBody();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch Segment Reverse ETL sync status", e);
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Reverse ETL sync ID")
        private final String syncId;

        @Schema(
            title = "Create sync response"
        )
        private final ReverseEtlSyncResponse.ReverseETLManualSync created;

        @Schema(
            title = "Final sync status",
            description = "Returned only when wait=true"
        )
        private final ReverseEtlSyncStatus status;
    }
}
