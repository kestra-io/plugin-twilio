# Kestra Twilio Plugin

## What

- Provides plugin components under `io.kestra.plugin.twilio`.
- Includes classes such as `TwilioAlert`, `TwilioTemplate`, `TwilioExecution`, `SendGridMailExecution`.

## Why

- What user problem does this solve? Teams need to integrate with the Twilio ecosystem from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps Twilio steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on Twilio.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `twilio`

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.twilio.notify.TwilioAlert`
- `io.kestra.plugin.twilio.notify.TwilioExecution`
- `io.kestra.plugin.twilio.segment.reverseetl.Status`
- `io.kestra.plugin.twilio.segment.reverseetl.Sync`
- `io.kestra.plugin.twilio.sendgrid.SendGridMailExecution`
- `io.kestra.plugin.twilio.sendgrid.SendGridMailSend`

### Project Structure

```
plugin-twilio/
├── src/main/java/io/kestra/plugin/twilio/sendgrid/
├── src/test/java/io/kestra/plugin/twilio/sendgrid/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
