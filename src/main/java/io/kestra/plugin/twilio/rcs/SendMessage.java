package io.kestra.plugin.twilio.rcs;

import java.util.List;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.twilio.AbstractMessageSend;

import io.swagger.v3.oas.annotations.media.Schema;
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
    title = "Send an RCS message via Twilio",
    description = """
        Posts a message to the Twilio Messages API from an RCS-enabled sender, using Account SID and Auth Token for basic authentication.
        Twilio falls back to SMS when the recipient's device or carrier does not support RCS, so the same task covers both cases without extra configuration.
        Set `contentSid` to send a rich RCS content template instead of plain text.
        Returns the Twilio message SID and delivery status from the API response.
        See the <a href="https://www.twilio.com/docs/channels/rcs">Twilio RCS documentation</a> for details.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Send an RCS alert on a failed flow execution.",
            full = true,
            code = """
                id: alert_on_failure
                namespace: company.team

                tasks:
                  - id: risky_step
                    type: io.kestra.plugin.scripts.shell.Commands
                    runner: PROCESS
                    commands:
                      - exit 1

                errors:
                  - id: send_rcs_alert
                    type: io.kestra.plugin.twilio.rcs.SendMessage
                    accountSID: "{{ secret('TWILIO_ACCOUNT_SID') }}"
                    authToken: "{{ secret('TWILIO_AUTH_TOKEN') }}"
                    from: "{{ secret('TWILIO_RCS_SENDER') }}"
                    to: "+15555550100"
                    body: "Flow {{ flow.id }} failed on execution {{ execution.id }}."
                """
        ),
        @Example(
            title = "Send a rich RCS message using a content template.",
            full = true,
            code = """
                id: rcs_rich_notification
                namespace: company.team

                inputs:
                  - id: recipient
                    type: STRING

                tasks:
                  - id: send_message
                    type: io.kestra.plugin.twilio.rcs.SendMessage
                    accountSID: "{{ secret('TWILIO_ACCOUNT_SID') }}"
                    authToken: "{{ secret('TWILIO_AUTH_TOKEN') }}"
                    from: "{{ secret('TWILIO_RCS_SENDER') }}"
                    to: "{{ inputs.recipient }}"
                    contentSid: "{{ secret('TWILIO_CONTENT_SID') }}"
                    body: "Your order has been shipped."

                  - id: log_result
                    type: io.kestra.plugin.core.log.Log
                    message: "Message sent. SID: {{ outputs.send_message.sid }}, status: {{ outputs.send_message.status }}"
                """
        ),
    }
)
public class SendMessage extends AbstractMessageSend {

    @Schema(
        title = "Content template SID",
        description = "SID of a Twilio Content API template (`HX...`) to render as a rich RCS message; when omitted the message body is sent as plain text"
    )
    @PluginProperty(group = "main")
    private Property<String> contentSid;

    @Override
    protected void additionalFormParameters(RunContext runContext, List<String> formParameters) throws Exception {
        runContext.render(contentSid).as(String.class)
            .filter(sid -> !sid.isBlank())
            .ifPresent(sid -> formParameters.add(formPair("ContentSid", sid)));
    }
}
