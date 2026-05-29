# How to use the Twilio plugin

The Twilio plugin connects Kestra workflows to Twilio Notify push messages, Segment Reverse ETL syncs, and SendGrid email delivery.

## Authentication

Authentication is configured per service.

**Twilio Notify:** Set `accountSID` and `authToken` on each `notify.*` task; requests use HTTP Basic auth. Set `url` to your Notify service endpoint (`https://notify.twilio.com/v1/Services/{ServiceSid}/Notifications`). Store both credentials as [secrets](https://kestra.io/docs/concepts/secret) and apply them globally with [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults).

**Segment:** Set `token` to a Segment Public API bearer token with Reverse ETL scopes. Optionally override `uri` (defaults to `https://api.segmentapis.com`) for custom endpoints. Store the token as a [secret](https://kestra.io/docs/concepts/secret).

**SendGrid:** Set `sendgridApiKey` to a SendGrid API key with mail-send permissions. Store it as a [secret](https://kestra.io/docs/concepts/secret).

## Tasks

`notify.TwilioAlert` posts a raw JSON payload to the Twilio Notify API. Use it in `errors` handlers for flow-level alerts — set `url`, `accountSID`, `authToken`, and `payload`. For flow-triggered execution summaries, use `notify.TwilioExecution` instead, which renders a bundled template with execution status and a UI link via `executionId`; extend it with `identity`, `tag`, `customMessage`, and `customFields`. Both tasks accept an `options` block for HTTP tuning (`connectTimeout`, `readIdleTimeout`, custom `headers`).

`segment.reverseetl.Sync` triggers a manual Reverse ETL sync — `sourceId`, `modelId`, and `subscriptionId` are all required. By default `wait` is `false` (fire-and-forget); set `wait: true` to poll until completion, controlled by `maxDuration` (default 1h) and `pollInterval` (default 5s). Set `errorOnFailing: true` to fail the task when the sync reports an error. Use `segment.reverseetl.Status` to check the status of an already-running sync by `modelId` and `syncId`.

`sendgrid.SendGridMailSend` composes and delivers an email — `from`, `to`, and `sendgridApiKey` are required, plus at least one of `htmlContent` or `textContent`. Optional fields include `cc`, `subject`, `attachments` (files from Kestra internal storage), and `embeddedImages` (inline images for HTML). For flow-triggered execution summaries, use `sendgrid.SendGridMailExecution` instead, which renders a bundled HTML and text template from `executionId`; extend it with `customMessage` and `customFields`.
