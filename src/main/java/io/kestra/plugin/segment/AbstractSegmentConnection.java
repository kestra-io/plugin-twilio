package io.kestra.plugin.segment;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.*;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.net.URI;

@SuperBuilder
@Getter
@NoArgsConstructor
public abstract class AbstractSegmentConnection extends Task {
    protected static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    protected static final String BASE_URL = "https://api.segmentapis.com";

    @Schema(title = "Segment API token")
    @NotNull
    protected Property<String> token;

    @Schema(title = "Segment API URL")
    @Builder.Default
    protected Property<String> uri = Property.ofValue(BASE_URL);

    protected <T> HttpResponse<T> request(RunContext runContext, String method, String path, Object body, Class<T> responseType) throws IOException, IllegalVariableEvaluationException, HttpClientException {
        HttpRequest.HttpRequestBuilder builder = HttpRequest.builder()
            .uri(URI.create(runContext.render(uri).as(String.class).orElse(BASE_URL) + path))
            .method(method)
            .addHeader("Authorization", "Bearer " +
                runContext.render(token).as(String.class).orElseThrow())
            .addHeader("Content-Type", "application/vnd.segment.v1alpha+json");

        if (body != null) {
            builder.body(HttpRequest.JsonRequestBody.builder().content(body).build());
        }

        try (HttpClient client = new HttpClient(runContext, HttpConfiguration.builder().build())) {
            HttpResponse<String> response = client.request(builder.build(), String.class);

            return HttpResponse.<T>builder()
                .status(response.getStatus())
                .headers(response.getHeaders())
                .body(MAPPER.readValue(response.getBody(), responseType))
                .build();
        }
    }
}
