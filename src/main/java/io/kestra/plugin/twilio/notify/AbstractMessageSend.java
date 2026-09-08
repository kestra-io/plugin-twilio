package io.kestra.plugin.twilio.notify;

import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.twilio.AbstractMessagesApiTask;

import io.swagger.v3.oas.annotations.media.Schema;
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
public abstract class AbstractMessageSend extends AbstractMessagesApiTask implements RunnableTask<AbstractMessageSend.Output> {

    @Override
    public Output run(RunContext runContext) throws Exception {
        var message = sendMessage(runContext);

        return Output.builder()
            .sid(message.getSid())
            .status(message.getStatus())
            .build();
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
