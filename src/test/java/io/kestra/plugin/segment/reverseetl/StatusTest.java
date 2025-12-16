package io.kestra.plugin.segment.reverseetl;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.segment.reverseetl.models.ReverseEtlSyncStatus;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@KestraTest
@WireMockTest(httpPort = 28181)
class StatusTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(get(urlEqualTo(
            "/reverse-etl-models/model-123/syncs/sync-456"
        )).willReturn(okJson(STATUS_RESPONSE)));

        RunContext runContext = runContextFactory.of(Map.of());

        Status task = Status.builder()
            .token(Property.ofValue("test-token"))
            .modelId(Property.ofValue("model-123"))
            .syncId(Property.ofValue("sync-456"))
            .uri(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .build();

        Status.Output output = task.run(runContext);

        ReverseEtlSyncStatus status = output.getStatus();

        assertThat(status.getSyncId(), is("sync-456"));
        assertThat(status.getStatus(), is("SUCCESS"));
        assertThat(status.isTerminal(), is(true));
        assertThat(status.isSuccessful(), is(true));
    }

    private static final String STATUS_RESPONSE = """
        {
          "data": {
            "reverseETLSyncStatus": {
              "syncId": "sync-456",
              "syncStatus": "SUCCESS"
            }
          }
        }
        """;
}
