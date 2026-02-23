package io.kestra.plugin.twilio.notify;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.plugins.notifications.ExecutionInterface;
import io.kestra.core.plugins.notifications.ExecutionService;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send execution summary via Twilio",
    description = "Uses the bundled template to send execution status, flow metadata, and a UI link through Twilio Notify. Intended for Flow-triggered alerts; for `errors` handlers, use TwilioAlert instead."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a Twilio notification on a failed flow execution.",
            full = true,
            code = """
                id: failure_alert
                namespace: company.team

                tasks:
                  - id: send_alert
                    type: io.kestra.plugin.twilio.notify.TwilioExecution
                    url: "{{ secret('TWILIO_NOTIFICATION_URL') }}" # format: https://notify.twilio.com/v1/Services/ISXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/Notifications
                    accountSID: "{{ secret('TWILIO_ACCOUNT_SID') }}"
                    authToken: "{{ secret('TWILIO_AUTH_TOKEN') }}"
                    identity: 0000001
                    executionId: "{{trigger.executionId}}"

                triggers:
                  - id: failed_prod_workflows
                    type: io.kestra.plugin.core.trigger.Flow
                    conditions:
                      - type: io.kestra.plugin.core.condition.ExecutionStatus
                        in:
                          - FAILED
                          - WARNING
                      - type: io.kestra.plugin.core.condition.ExecutionNamespace
                        namespace: prod
                        prefix: true
                """
        )
    },
    aliases = "io.kestra.plugin.notifications.twilio.TwilioExecution"
)
public class TwilioExecution extends TwilioTemplate implements ExecutionInterface {
    @Schema(
        title = "Execution ID",
        description = "Defaults to the current execution ID using an expression"
    )
    @Builder.Default
    private final Property<String> executionId = Property.ofExpression("{{ execution.id }}");

    @Schema(
        title = "Custom fields",
        description = "Additional key-value pairs merged into the rendered template context"
    )
    private Property<Map<String, Object>> customFields;

    @Schema(
        title = "Custom message",
        description = "Optional message rendered into the template alongside execution details"
    )
    private Property<String> customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = Property.ofValue("twilio-template.peb");
        this.templateRenderMap = Property.ofValue(ExecutionService.executionMap(runContext, this));

        return super.run(runContext);
    }
}
