# Kestra Twilio Plugin

## What

- Provides plugin components under `io.kestra.plugin.twilio`.
- Includes classes such as `TwilioAlert`, `TwilioTemplate`, `TwilioExecution`, `SendGridMailExecution`.

## Why

- This plugin integrates Kestra with Twilio.
- It provides tasks that integrate with the Twilio ecosystem.

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
