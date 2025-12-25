package io.kestra.plugin.twilio.segment.reverseetl.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReverseEtlSyncStatusResponse {
    private Data data;

    @Getter
    public static class Data {
        private ReverseEtlSyncStatus reverseETLSyncStatus;
    }
}
