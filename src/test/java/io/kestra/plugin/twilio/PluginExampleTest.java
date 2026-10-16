package io.kestra.plugin.twilio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.twilio.notify.TwilioAlert;
import io.kestra.plugin.twilio.notify.TwilioExecution;
import io.kestra.plugin.twilio.notify.rcs.SendMessage;
import io.kestra.plugin.twilio.segment.reverseetl.Status;
import io.kestra.plugin.twilio.segment.reverseetl.Sync;
import io.kestra.plugin.twilio.sendgrid.SendGridMailExecution;
import io.kestra.plugin.twilio.sendgrid.SendGridMailSend;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

class PluginExampleTest {

    /** Flow sections holding task definitions, so examples are checked wherever they are used. */
    private static final List<String> TASK_SECTIONS = List.of("tasks", "errors", "triggers");

    private static final List<Class<?>> PLUGINS = List.of(
        SendGridMailSend.class,
        SendGridMailExecution.class,
        TwilioAlert.class,
        TwilioExecution.class,
        io.kestra.plugin.twilio.notify.mms.Send.class,
        io.kestra.plugin.twilio.notify.sms.Send.class,
        SendMessage.class,
        Status.class,
        Sync.class
    );

    @Test
    @DisplayName("Plugin examples only reference existing task properties")
    void examplesOnlyUseKnownProperties() throws Exception {
        List<String> failures = new ArrayList<>();
        ObjectMapper yaml = JacksonMapper.ofYaml();

        for (Class<?> plugin : PLUGINS) {
            Plugin annotation = plugin.getAnnotation(Plugin.class);
            List<String> types = Arrays.asList(annotation.aliases());

            for (Example example : annotation.examples()) {
                if (!example.full()) {
                    continue;
                }

                for (JsonNode task : tasksOf(yaml, example, plugin)) {
                    String type = task.path("type").asText();
                    if (!type.equals(plugin.getName()) && !types.contains(type)) {
                        continue;
                    }

                    try {
                        // JacksonMapper.ofJson(true) rejects unknown properties, so a typo such as
                        // `htmlTextContent` fails here exactly as it fails once the example is copied into a flow.
                        Object deserialized = JacksonMapper.ofJson(true).treeToValue(task, plugin);
                        if (!plugin.isInstance(deserialized)) {
                            failures.add(type + " (" + example.title() + ") did not deserialize to " + plugin.getName());
                        }
                    } catch (Exception e) {
                        failures.add(type + " (" + example.title() + "): " + rootMessage(e));
                    }
                }
            }
        }

        assertThat("Invalid plugin examples", failures, empty());
    }

    private static List<JsonNode> tasksOf(ObjectMapper yaml, Example example, Class<?> plugin) throws Exception {
        JsonNode flow = yaml.readTree(String.join("\n", example.code()));

        List<JsonNode> tasks = new ArrayList<>();
        for (String section : TASK_SECTIONS) {
            JsonNode nodes = flow.get(section);
            if (nodes != null && nodes.isArray()) {
                nodes.forEach(tasks::add);
            }
        }

        if (tasks.isEmpty()) {
            throw new IllegalStateException("No task found in example \"" + example.title() + "\" of " + plugin.getName());
        }

        return tasks;
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }

        return root.getMessage();
    }
}
