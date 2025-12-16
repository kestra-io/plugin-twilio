package io.kestra.plugin.segment.reverseetl;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
@WireMockTest(httpPort = 28181)
class SyncTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/reverse-etl-syncs"))
            .willReturn(okJson("""
                {
                  "data": {
                    "reverseETLManualSync": {
                      "syncId": "sync-123",
                      "startedAt": "2025-01-01T00:00:00Z"
                    }
                  }
                }
            """)));

        stubFor(get(urlPathEqualTo("/reverse-etl-models/model/syncs/sync-123"))
            .inScenario("poll")
            .whenScenarioStateIs(STARTED)
            .willReturn(okJson("""
                {
                  "data": {
                    "reverseETLSyncStatus": {
                      "syncStatus": "IN_PROGRESS"
                    }
                  }
                }
            """))
            .willSetStateTo("done"));

        stubFor(get(urlPathEqualTo("/reverse-etl-models/model/syncs/sync-123"))
            .inScenario("poll")
            .whenScenarioStateIs("done")
            .willReturn(okJson("""
                {
                  "data": {
                    "reverseETLSyncStatus": {
                      "syncStatus": "SUCCESS"
                    }
                  }
                }
            """)));

        RunContext runContext = runContextFactory.of(Map.of());

        Sync task = Sync.builder()
            .token(Property.ofValue("test-token"))
            .sourceId(Property.ofValue("source"))
            .modelId(Property.ofValue("model"))
            .subscriptionId(Property.ofValue("subscription"))
            .wait(Property.ofValue(true))
            .pollInterval(Property.ofValue(java.time.Duration.ofMillis(10)))
            .uri(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .build();

        Sync.Output output = task.run(runContext);

        assertThat(output.getSyncId(), is("sync-123"));
        assertThat(output.getStatus(), notNullValue());
        assertThat(output.getStatus().getStatus(), is("SUCCESS"));
    }

    @Test
    void runWithoutWait(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(post(urlPathEqualTo("/reverse-etl-syncs"))
            .willReturn(okJson("""
            {
              "data": {
                "reverseETLManualSync": {
                  "syncId": "sync-456",
                  "startedAt": "2025-01-01T00:00:00Z"
                }
              }
            }
        """)));

        RunContext runContext = runContextFactory.of(Map.of());

        Sync task = Sync.builder()
            .token(Property.ofValue("test-token"))
            .sourceId(Property.ofValue("source"))
            .modelId(Property.ofValue("model"))
            .subscriptionId(Property.ofValue("subscription"))
            .wait(Property.ofValue(false))
            .uri(Property.ofValue(wireMockRuntimeInfo.getHttpBaseUrl()))
            .build();

        Sync.Output output = task.run(runContext);

        assertThat(output.getSyncId(), is("sync-456"));
        assertThat(output.getCreated(), notNullValue());
        assertThat(output.getCreated().getSyncId(), is("sync-456"));
        assertThat(output.getStatus(), is(nullValue()));
    }
}
