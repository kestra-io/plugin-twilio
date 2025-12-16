package io.kestra.plugin.segment.reverseetl.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReverseEtlSyncStatus {
    @JsonProperty("syncId")
    private String syncId;

    @JsonProperty("syncStatus")
    private String status;

    private String sourceId;

    private String modelId;

    private String duration;

    private String startedAt;

    private String finishedAt;

    private ExtractPhase extractPhase;

    private LoadPhase loadPhase;

    private String error;

    private String errorCode;

    public boolean isTerminal() {
        return status != null && Set.of("SUCCESS", "FAIL").contains(status);
    }

    public boolean isSuccessful() {
        return "SUCCESS".equals(status);
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtractPhase {
        private String addedCount;
        private String updatedCount;
        private String deletedCount;
        private String extractCount;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LoadPhase {
        private String deliverSuccessCount;
        private String deliverFailureCount;
    }
}
