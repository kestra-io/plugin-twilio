package io.kestra.plugin.twilio.sendgrid;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Attachments;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send workflow email through SendGrid",
    description = "Delivers HTML or text emails with optional attachments using the SendGrid API and an API key. Supports inline images and fails the task on non-2xx responses."
)
@Plugin(
    examples = {
        @Example(
            title = "Send an email on a failed flow execution.",
            full = true,
            code = """
                id: unreliable_flow
                namespace: company.team

                tasks:
                  - id: fail
                    type: io.kestra.plugin.scripts.shell.Commands
                    runner: PROCESS
                    commands:
                      - exit 1

                errors:
                  - id: send_email
                    type: io.kestra.plugin.twilio.sendgrid.SendGridMailSend
                    from: hello@kestra.io
                    to:
                      - hello@kestra.io
                    sendgridApiKey: "{{ secret('SENDGRID_API_KEY') }}"
                    subject: "Kestra workflow failed for the flow {{flow.id}} in the namespace {{flow.namespace}}"
                    htmlTextContent: "Failure alert for flow {{ flow.namespace }}.{{ flow.id }} with ID {{ execution.id }}"
                """
        )
    },
    aliases = "io.kestra.plugin.notifications.sendgrid.SendGridMailSend"
)
public class SendGridMailSend extends Task implements RunnableTask<SendGridMailSend.Output> {
    /* Server info */

    @Schema(
        title = "SendGrid API key",
        description = "API key used to authenticate SendGrid requests; store as a secret"
    )
    @NotBlank
    @PluginProperty(dynamic = true)
    private String sendgridApiKey;

    /* Mail info */
    @Schema(
        title = "Sender email address",
        description = "Must comply with RFC 2822 formatting"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    private String from;

    @Schema(
        title = "Recipient email addresses",
        description = "Each address must comply with RFC 2822 format"
    )
    @NotEmpty
    @PluginProperty(dynamic = true)
    private List<String> to;

    @Schema(
        title = "Cc recipients",
        description = "Optional carbon-copy recipients in RFC 2822 format"
    )
    private Property<List<String>> cc;

    @Schema(
        title = "Email subject",
        description = "Optional subject line rendered from flow variables"
    )
    private Property<String> subject;

    @Schema(
        title = "HTML body",
        description = "Optional HTML content. When both HTML and text are provided, email clients treat them as alternatives and typically favor HTML."
    )
    protected Property<String> htmlContent;

    @Schema(
        title = "Plain text body",
        description = "Optional text content. When both HTML and text are provided, clients choose based on capability."
    )
    protected Property<String> textContent;

    @Schema(
        title = "File attachments",
        description = "List of files loaded from Kestra storage and attached to the email; delivered as downloadable attachments"
    )
    private List<Attachment> attachments;

    @Schema(
        title = "Inline embedded images",
        description = "Images loaded from storage and attached with inline disposition for HTML content"
    )
    private List<Attachment> embeddedImages;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

        Logger logger = runContext.logger();

        logger.debug("Sending an email to {}", runContext.render(to));

        Mail mail = new Mail();

        Email fromEmail = new Email(runContext.render(this.from));
        mail.setFrom(fromEmail);

        Personalization personalization = new Personalization();

        runContext.render(this.to).stream().map(Email::new).forEach(personalization::addTo);

        personalization.setSubject(runContext.render(this.subject).as(String.class).orElse(null));

        if (this.textContent != null) {
            var renderedText = runContext.render(this.textContent).as(String.class);
            final String textContent = renderedText.isEmpty() ? "Please view this email in a modern email client" : renderedText.get();
            Content plainTextContent = new Content(ContentType.TEXT_PLAIN.getMimeType(), textContent);
            mail.addContent(plainTextContent);
        }

        if (runContext.render(this.htmlContent).as(String.class).isPresent()) {
            Content htmlContent = new Content(ContentType.TEXT_HTML.getMimeType(), runContext.render(this.htmlContent).as(String.class).get());
            mail.addContent(htmlContent);
        }

        if (this.attachments != null) {
            this.attachmentResources(this.attachments, runContext).stream()
                .peek(attachment -> attachment.setDisposition("attachment"))
                .forEach(mail::addAttachments);
        }

        if (this.embeddedImages != null) {
            this.attachmentResources(this.embeddedImages, runContext).stream()
                .peek(attachment -> attachment.setDisposition("inline"))
                .forEach(mail::addAttachments);
        }

        final List<String> renderedCcList = runContext.render(this.cc).asList(String.class);
        if (!renderedCcList.isEmpty()) {
            renderedCcList.stream().map(Email::new).forEach(personalization::addCc);
        }
        mail.addPersonalization(personalization);

        SendGrid sendGrid = new SendGrid(runContext.render(this.sendgridApiKey));

        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());

        Response api = sendGrid.api(request);
        String body = api.getBody();
        Map<String, String> headers = api.getHeaders();
        int statusCode = api.getStatusCode();

        if (statusCode/100 != 2) {
          throw new RuntimeException("SendGrid API failed with status code: " + statusCode + " and body: " + body);
        }

        return Output.builder().body(body).headers(headers).statusCode(statusCode).build();
    }

    private List<Attachments> attachmentResources(List<Attachment> list, RunContext runContext) throws Exception {
        return list
            .stream()
            .map(throwFunction(attachment -> {
                InputStream inputStream = runContext.storage()
                    .getFile(URI.create(runContext.render(attachment.getUri()).as(String.class).get()));

                return new Attachments.Builder(runContext.render(attachment.getName()).as(String.class).get(), inputStream)
                    .withType(runContext.render(attachment.getContentType()).as(String.class).get()).build();
            }))
            .collect(Collectors.toList());
    }

    @Getter
    @Builder
    @Jacksonized
    public static class Attachment {
        @Schema(
            title = "Attachment URI",
            description = "URI in Kestra internal storage that supplies the file content"
        )
        @NotNull
        private Property<String> uri;

        @Schema(
            title = "Attachment filename",
            description = "Filename presented to recipients, e.g., 'report.pdf'"
        )
        @NotNull
        private Property<String> name;

        @Schema(
            title = "Attachment content type",
            description = "MIME type for the attachment; defaults to application/octet-stream"
        )
        @NotNull
        @Builder.Default
        private Property<String> contentType = Property.ofValue("application/octet-stream");
    }

    @Getter
    @Builder
    public static class Output implements io.kestra.core.models.tasks.Output {
        private String body;
        private Map<String, String> headers;
        private int statusCode;
    }
}
