package io.kestra.plugin.twilio.notify.mms;

import java.util.List;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.twilio.notify.AbstractMessageSend;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
@Schema(
    title = "Send an MMS via Twilio Programmable Messaging",
    description = """
        Posts a multimedia message to the Twilio Messages API using Account SID and Auth Token for basic authentication.
        Supports one or more media URLs attached to the message.
        Returns the Twilio message SID and delivery status from the API response.
        See the <a href="https://www.twilio.com/docs/messaging/api/message-resource#create-a-message-resource">Twilio documentation</a> for details.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Send an MMS on a failed flow execution.",
            full = true,
            code = """
                id: mms_on_failure
                namespace: company.team

                tasks:
                  - id: fail
                    type: io.kestra.plugin.scripts.shell.Commands
                    runner: PROCESS
                    commands:
                      - exit 1

                errors:
                  - id: alert_on_failure
                    type: io.kestra.plugin.twilio.notify.mms.Send
                    accountSID: "{{ secret('TWILIO_ACCOUNT_SID') }}"
                    authToken: "{{ secret('TWILIO_AUTH_TOKEN') }}"
                    from: "{{ secret('TWILIO_FROM_NUMBER') }}"
                    to: "+15555550100"
                    body: "Flow {{ flow.id }} failed on execution {{ execution.id }}."
                    mediaUrls:
                      - "https://demo.twilio.com/owl.png"
                """
        ),
        @Example(
            title = "Send an MMS message with one media attachment.",
            full = true,
            code = """
                id: send_mms
                namespace: company.team

                tasks:
                  - id: send_mms
                    type: io.kestra.plugin.twilio.notify.mms.Send
                    accountSID: "{{ secret('TWILIO_ACCOUNT_SID') }}"
                    authToken: "{{ secret('TWILIO_AUTH_TOKEN') }}"
                    from: "{{ secret('TWILIO_FROM_NUMBER') }}"
                    to: "+15555550100"
                    body: "Here is your report."
                    mediaUrls:
                      - "https://example.com/report.png"
                """
        ),
    }
)
public class Send extends AbstractMessageSend {

    @NotNull
    @Schema(
        title = "Media URLs",
        description = "One or more publicly accessible URLs for media to attach; Twilio accepts up to 10 per message"
    )
    @PluginProperty(group = "main")
    private Property<List<String>> mediaUrls;

    @Override
    protected void additionalFormParameters(RunContext runContext, List<String> formParameters) throws Exception {
        var rMediaUrls = runContext.render(mediaUrls).asList(String.class);

        if (rMediaUrls.isEmpty()) {
            throw new IllegalArgumentException("mediaUrls must contain at least one URL");
        }

        rMediaUrls.forEach(url -> formParameters.add(formPair("MediaUrl", url)));
    }
}
