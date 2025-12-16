package io.kestra.plugin.segment.reverseetl.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReverseEtlSyncRequest {
    private String sourceId;

    private String modelId;

    private String subscriptionId;
}
