package io.kestra.plugin.twilio.sendgrid;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
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
    title = "Email execution summary via SendGrid",
    description = "Uses bundled HTML and text templates to email execution status, flow metadata, and a UI link through SendGrid. Intended for Flow-triggered alerts; for `errors` handlers, use SendGridMailSend instead."
)
@Plugin(
    examples = {
        @Example(
            title = "Send an SendGrid email notification on a failed flow execution.",
            full = true,
            code = """
                id: failure_alert
                namespace: company.team

                tasks:
                  - id: send_alert
                    type: io.kestra.plugin.twilio.sendgrid.SendGridMailExecution
                    to:
                      - hello@kestra.io
                    from: hello@kestra.io
                    subject: "The workflow execution {{trigger.executionId}} failed for the flow {{trigger.flowId}} in the namespace {{trigger.namespace}}"
                    sendgridApiKey: "{{ secret('SENDGRID_API_KEY') }}"
                    executionId: "{{ trigger.executionId }}"

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
    aliases = "io.kestra.plugin.notifications.sendgrid.SendGridMailExecution"
)
public class SendGridMailExecution extends SendGridMailTemplate implements ExecutionInterface {
    @Schema(
        title = "Execution ID",
        description = "Defaults to the current execution ID using an expression"
    )
    @Builder.Default
    private final Property<String> executionId = Property.ofExpression("{{ execution.id }}");

    @Schema(
        title = "Custom fields",
        description = "Additional key-value pairs merged into the template context"
    )
    private Property<Map<String, Object>> customFields;

    @Schema(
        title = "Custom message",
        description = "Optional message rendered into the template alongside execution details"
    )
    private Property<String> customMessage;

    @Override
    public Output run(RunContext runContext) throws Exception {
        this.templateUri = Property.ofValue("sendgrid-mail-template.hbs.peb");
        this.textTemplateUri = Property.ofValue("sendgrid-text-template.hbs.peb");
        this.templateRenderMap = Property.ofValue(ExecutionService.executionMap(runContext, this));

        return super.run(runContext);
    }
}
