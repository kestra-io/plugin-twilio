package io.kestra.plugin.twilio.notify;

import java.util.Optional;
import java.util.regex.Pattern;

import com.twilio.exception.ApiException;
import com.twilio.http.NetworkHttpClient;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
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
    private static final Pattern ACCOUNT_SID_PATTERN = Pattern.compile("AC[0-9a-fA-F]{32}");
    private static final Pattern MESSAGING_SERVICE_SID_PATTERN = Pattern.compile("MG[0-9a-fA-F]{32}");

    @NotNull
    @Schema(
        title = "Twilio Account SID",
        description = "The Account SID used for basic authentication and to construct the Messages API URL"
    )
    @PluginProperty(group = "connection")
    private Property<String> accountSID;

    @NotNull
    @ToString.Exclude
    @Schema(
        title = "Twilio Auth Token",
        description = "The Auth Token paired with the Account SID; store as a Kestra secret"
    )
    @PluginProperty(secret = true, group = "connection")
    private Property<String> authToken;

    @Schema(
        title = "Sender phone number",
        description = "The Twilio phone number, alphanumeric sender ID, or short code to send from. Mutually exclusive with `messagingServiceSid`"
    )
    @PluginProperty(group = "main")
    private Property<String> from;

    @Schema(
        title = "Messaging Service SID",
        description = "SID of a Twilio Messaging Service (`MG...`) to send through, which picks the sender from the service's pool. Required for channels configured on a Messaging Service, such as RCS. Mutually exclusive with `from`"
    )
    @PluginProperty(group = "main")
    private Property<String> messagingServiceSid;

    @NotNull
    @Schema(
        title = "Recipient phone number",
        description = "The destination phone number in E.164 format, e.g. +15555550100"
    )
    @PluginProperty(group = "main")
    private Property<String> to;

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

    // False for channels that can carry content without a text body, e.g. an RCS content template.
    protected boolean requiresBody() {
        return true;
    }

    // Subclasses set extra fields on the creator (e.g. MediaUrl, ContentSid). Default: none.
    protected void configureCreator(RunContext runContext, MessageCreator creator, Optional<String> renderedBody) throws Exception {
    }

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rAccountSID = runContext.render(accountSID).as(String.class).orElseThrow(() -> new IllegalArgumentException("accountSID is required"));
        if (!ACCOUNT_SID_PATTERN.matcher(rAccountSID).matches()) {
            throw new IllegalArgumentException("accountSID must be a valid Twilio Account SID (AC followed by 32 hex characters)");
        }
        var rAuthToken = runContext.render(authToken).as(String.class).orElseThrow(() -> new IllegalArgumentException("authToken is required"));
        var rTo = runContext.render(to).as(String.class).orElseThrow(() -> new IllegalArgumentException("to is required"));

        var rBody = renderedBody(runContext);
        if (requiresBody() && rBody.isEmpty()) {
            throw new IllegalArgumentException("body is required");
        }

        var creator = creator(runContext, rTo, rBody.orElse(null));
        configureCreator(runContext, creator, rBody);

        runContext.logger().debug("Sending Twilio message to {}", rTo);

        Message message;
        try {
            message = creator.create(restClient(rAccountSID, rAuthToken));
        } catch (ApiException e) {
            // a null status means the SDK never got a usable response, so say that rather than leak a parser error
            if (e.getStatusCode() == null) {
                throw new TwilioApiException(
                    "Twilio Messages API returned " + (isEmptyResponse(e) ? "an empty body" : "an unparseable body")
                        + ": " + e.getMessage(),
                    e
                );
            }

            throw new TwilioApiException(
                "Twilio Messages API returned HTTP " + e.getStatusCode() + ": " + e.getMessage()
                    + (e.getMoreInfo() == null ? "" : " " + e.getMoreInfo())
                    + ". Check the request parameters (e.g. 'to'/'from' format) and Twilio account configuration.",
                e
            );
        }

        runContext.logger().info("Message sent, sid={} status={}", message.getSid(), message.getStatus());

        return Output.builder()
            .sid(message.getSid())
            .status(message.getStatus() == null ? null : message.getStatus().toString())
            .build();
    }

    private static boolean isEmptyResponse(ApiException e) {
        return e.getMessage() != null && e.getMessage().contains("end-of-input");
    }

    /** Either `from` or `messagingServiceSid` identifies the sender, exactly as the form build did. */
    private MessageCreator creator(RunContext runContext, String rTo, String rBody) throws Exception {
        var rMessagingServiceSid = runContext.render(messagingServiceSid).as(String.class).filter(v -> !v.isBlank());
        var rFrom = runContext.render(from).as(String.class).filter(v -> !v.isBlank());
        var to = new PhoneNumber(rTo);

        if (rMessagingServiceSid.isPresent() && rFrom.isPresent()) {
            throw new IllegalArgumentException("from and messagingServiceSid are mutually exclusive, set only one");
        }

        if (rMessagingServiceSid.isPresent()) {
            if (!MESSAGING_SERVICE_SID_PATTERN.matcher(rMessagingServiceSid.get()).matches()) {
                throw new IllegalArgumentException("messagingServiceSid must be a valid Twilio Messaging Service SID (MG followed by 32 hex characters)");
            }

            return new MessageCreator(to, rMessagingServiceSid.get(), rBody);
        }

        var sender = rFrom.orElseThrow(() -> new IllegalArgumentException("either from or messagingServiceSid is required"));
        if (MESSAGING_SERVICE_SID_PATTERN.matcher(sender).matches()) {
            throw new IllegalArgumentException("from looks like a Messaging Service SID, set it on messagingServiceSid instead");
        }

        return new MessageCreator(to, new PhoneNumber(sender), rBody);
    }

    /**
     * The SDK has no base URL setting, so a non-default `baseUrl()` is applied by rewriting each request. That keeps
     * the existing test seam working and lets a Twilio-compatible proxy be used.
     */
    private TwilioRestClient restClient(String accountSid, String authToken) {
        var builder = new TwilioRestClient.Builder(accountSid, authToken).accountSid(accountSid);

        if (!DEFAULT_BASE_URL.equals(baseUrl())) {
            builder.httpClient(new RebasingHttpClient(baseUrl()));
        }

        return builder.build();
    }

    private static final class RebasingHttpClient extends com.twilio.http.HttpClient {
        private final String baseUrl;
        private final com.twilio.http.HttpClient delegate = new NetworkHttpClient();

        private RebasingHttpClient(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        @Override
        public com.twilio.http.Response makeRequest(com.twilio.http.Request original) {
            var rebased = new com.twilio.http.Request(
                original.getMethod(),
                original.getUrl().replace(DEFAULT_BASE_URL, baseUrl)
            );

            original.getPostParams().forEach((name, values) -> values.forEach(value -> rebased.addPostParam(name, value)));
            original.getQueryParams().forEach((name, values) -> values.forEach(value -> rebased.addQueryParam(name, value)));
            original.getHeaderParams().forEach((name, values) -> values.forEach(value -> rebased.addHeaderParam(name, value)));
            rebased.setAuth(original.getUsername(), original.getPassword());

            return delegate.makeRequest(rebased);
        }
    }

    private Optional<String> renderedBody(RunContext runContext) throws Exception {
        return runContext.render(body).as(String.class).filter(value -> !value.isBlank());
    }

    private static class TwilioApiException extends RuntimeException {
        TwilioApiException(String message) {
            super(message);
        }

        TwilioApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Twilio message SID", description = "Unique identifier assigned by Twilio to the sent message")
        private final String sid;

        @Schema(title = "Message status", description = "Delivery status returned by Twilio, e.g. queued, sent, delivered")
        private final String status;
    }
}
