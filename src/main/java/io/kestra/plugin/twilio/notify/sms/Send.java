package io.kestra.plugin.twilio.notify.sms;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.plugin.twilio.notify.AbstractMessageSend;

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
    title = "Send an SMS via Twilio Programmable Messaging",
    description = """
        Posts a message to the Twilio Messages API using Account SID and Auth Token for basic authentication.
        Returns the Twilio message SID and delivery status from the API response.
        See the <a href="https://www.twilio.com/docs/messaging/api/message-resource#create-a-message-resource">Twilio documentation</a> for details.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Send an SMS on a failed flow execution.",
            full = true,
            code = """
                id: sms_on_failure
                namespace: company.team

                tasks:
                  - id: fail
                    type: io.kestra.plugin.scripts.shell.Commands
                    runner: PROCESS
                    commands:
                      - exit 1

                errors:
                  - id: alert_on_failure
                    type: io.kestra.plugin.twilio.notify.sms.Send
                    accountSID: "{{ secret('TWILIO_ACCOUNT_SID') }}"
                    authToken: "{{ secret('TWILIO_AUTH_TOKEN') }}"
                    from: "{{ secret('TWILIO_FROM_NUMBER') }}"
                    to: "+15555550100"
                    body: "Flow {{ flow.id }} failed on execution {{ execution.id }}."
                """
        ),
        @Example(
            title = "Send an SMS message.",
            full = true,
            code = """
                id: send_sms
                namespace: company.team

                tasks:
                  - id: send_sms
                    type: io.kestra.plugin.twilio.notify.sms.Send
                    accountSID: "{{ secret('TWILIO_ACCOUNT_SID') }}"
                    authToken: "{{ secret('TWILIO_AUTH_TOKEN') }}"
                    from: "{{ secret('TWILIO_FROM_NUMBER') }}"
                    to: "+15555550100"
                    body: "Hello from Kestra."
                """
        ),
    }
)
public class Send extends AbstractMessageSend {
}
