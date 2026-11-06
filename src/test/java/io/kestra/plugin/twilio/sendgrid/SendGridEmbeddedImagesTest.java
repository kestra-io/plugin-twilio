package io.kestra.plugin.twilio.sendgrid;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sendgrid.helpers.mail.objects.Attachments;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@KestraTest
class SendGridEmbeddedImagesTest {

    @Inject
    private RunContextFactory runContextFactory;

    private URI putFile(RunContext runContext) throws Exception {
        var file = File.createTempFile("logo", ".png");
        Files.write(file.toPath(), new byte[] {1, 2, 3});
        return runContext.storage().putFile(file);
    }

    private SendGridMailSend.Attachment attachment(URI uri, String contentId) {
        var builder = SendGridMailSend.Attachment.builder()
            .uri(Property.ofValue(uri.toString()))
            .name(Property.ofValue("logo.png"))
            .contentType(Property.ofValue("image/png"));
        if (contentId != null) {
            builder.contentId(Property.ofValue(contentId));
        }
        return builder.build();
    }

    @Test
    void embeddedImagesAreInlineWithTheNameAsContentId() throws Exception {
        var runContext = runContextFactory.of();
        var uri = putFile(runContext);

        var result = new SendGridMailSend().attachmentResources(List.of(attachment(uri, null)), runContext, true);

        assertThat(result.getFirst().getDisposition(), is("inline"));
        assertThat(result.getFirst().getContentId(), is("logo.png"));
        assertThat(result.getFirst().getFilename(), is("logo.png"));
    }

    @Test
    void embeddedImagesUseTheGivenContentId() throws Exception {
        var runContext = runContextFactory.of();
        var uri = putFile(runContext);

        var result = new SendGridMailSend().attachmentResources(List.of(attachment(uri, "company-logo")), runContext, true);

        assertThat(result.getFirst().getContentId(), is("company-logo"));
    }

    @Test
    void blankContentIdFallsBackToTheName() throws Exception {
        var runContext = runContextFactory.of();
        var uri = putFile(runContext);

        var result = new SendGridMailSend().attachmentResources(List.of(attachment(uri, " ")), runContext, true);

        assertThat(result.getFirst().getContentId(), is("logo.png"));
    }

    @Test
    void regularAttachmentsHaveNoContentId() throws Exception {
        var runContext = runContextFactory.of();
        var uri = putFile(runContext);

        var result = new SendGridMailSend().attachmentResources(List.of(attachment(uri, null)), runContext, false);

        assertThat(result.getFirst().getDisposition(), is("attachment"));
        assertThat(result.getFirst().getContentId(), is(nullValue()));
    }
}
