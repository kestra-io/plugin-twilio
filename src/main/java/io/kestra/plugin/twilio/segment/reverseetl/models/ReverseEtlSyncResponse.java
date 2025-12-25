package io.kestra.plugin.twilio.segment.reverseetl.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.Instant;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReverseEtlSyncResponse {
    private Data data;

    @Getter
    public static class Data {
        private ReverseETLManualSync reverseETLManualSync;
    }

    @Getter
    public static class ReverseETLManualSync {
        private String syncId;
        private Instant startedAt;
    }
}
