package io.kestra.plugin.twilio.notify;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.twilio.AbstractTwilioConnection;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractMessageSend extends AbstractTwilioConnection implements RunnableTask<AbstractMessageSend.Output> {

    private static final String DEFAULT_BASE_URL = "https://api.twilio.com";
    private static final String ACCOUNT_SID_PATTERN = "AC[0-9a-fA-F]{32}";

    @NotNull
    @Schema(
        title = "Twilio Account SID",
        description = "The Account SID used for basic authentication and to construct the Messages API URL"
    )
    @PluginProperty(group = "connection")
    private Property<String> accountSID;

    @NotNull
    @Schema(
        title = "Twilio Auth Token",
        description = "The Auth Token paired with the Account SID; store as a Kestra secret"
    )
    @PluginProperty(secret = true, group = "connection")
    private Property<String> authToken;

    @NotNull
    @Schema(
        title = "Sender phone number or Messaging Service SID",
        description = "The Twilio number or Messaging Service SID to send from"
    )
    @PluginProperty(group = "main")
    private Property<String> from;

    @NotNull
    @Schema(
        title = "Recipient phone number",
        description = "The destination phone number in E.164 format, e.g. +15555550100"
    )
    @PluginProperty(group = "main")
    private Property<String> to;

    @NotNull
    @Schema(
        title = "Message body",
        description = "The text content of the message"
    )
    @PluginProperty(group = "main")
    private Property<String> body;

    // Twilio Messages API base URL. Not a flow property; overridden only by tests via a subclass.
    protected String baseUrl() {
        return DEFAULT_BASE_URL;
    }

    // Subclasses add extra form parameters (e.g. MediaUrl). Default: none.
    protected void additionalFormParameters(RunContext runContext, List<String> formParameters) throws Exception {
    }

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rAccountSID = runContext.render(accountSID).as(String.class).orElseThrow(() -> new IllegalArgumentException("accountSID is required"));
        if (!rAccountSID.matches(ACCOUNT_SID_PATTERN)) {
            throw new IllegalArgumentException("accountSID must be a valid Twilio Account SID (AC followed by 32 hex characters)");
        }
        var rAuthToken = runContext.render(authToken).as(String.class).orElseThrow(() -> new IllegalArgumentException("authToken is required"));
        var rFrom = runContext.render(from).as(String.class).orElseThrow(() -> new IllegalArgumentException("from is required"));
        var rTo = runContext.render(to).as(String.class).orElseThrow(() -> new IllegalArgumentException("to is required"));
        var rBody = runContext.render(body).as(String.class).orElseThrow(() -> new IllegalArgumentException("body is required"));

        List<String> formParameters = new ArrayList<>();
        formParameters.add(formPair("From", rFrom));
        formParameters.add(formPair("To", rTo));
        formParameters.add(formPair("Body", rBody));
        additionalFormParameters(runContext, formParameters);

        var url = baseUrl() + "/2010-04-01/Accounts/" + rAccountSID + "/Messages.json";
        var authHeader = Base64.getEncoder().encodeToString(
            (rAccountSID + ":" + rAuthToken).getBytes(StandardCharsets.UTF_8)
        );

        runContext.logger().debug("Sending Twilio message to {}", rTo);

        try (var client = new HttpClient(runContext, super.httpClientConfigurationWithOptions())) {
            var request = createRequestBuilder(runContext)
                .addHeader("Authorization", "Basic " + authHeader)
                .uri(URI.create(url))
                .method("POST")
                .body(HttpRequest.StringRequestBody.builder()
                    .contentType("application/x-www-form-urlencoded")
                    .charset(StandardCharsets.UTF_8)
                    .content(String.join("&", formParameters))
                    .build())
                .build();

            HttpResponse<String> response;
            try {
                response = client.request(request, String.class);
            } catch (HttpClientResponseException e) {
                throw new RuntimeException(
                    "Twilio Messages API returned HTTP " + e.getResponse().getStatus().getCode() + ": " + e.getResponse().getBody(),
                    e
                );
            }

            var statusCode = response.getStatus().getCode();
            if (statusCode != 201) {
                throw new RuntimeException(
                    "Twilio Messages API returned HTTP " + statusCode + ": " + response.getBody()
                );
            }

            var parsed = JacksonMapper.ofJson().readValue(response.getBody(), MessageResponse.class);
            runContext.logger().info("Message sent, sid={} status={}", parsed.getSid(), parsed.getStatus());

            return Output.builder()
                .sid(parsed.getSid())
                .status(parsed.getStatus())
                .build();
        }
    }

    protected static String formPair(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Twilio message SID", description = "Unique identifier assigned by Twilio to the sent message")
        private final String sid;

        @Schema(title = "Message status", description = "Delivery status returned by Twilio, e.g. queued, sent, delivered")
        private final String status;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MessageResponse {
        private String sid;
        private String status;
    }
}
