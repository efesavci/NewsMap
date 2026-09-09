# NewsMap V3 — Personalization Roadmap

V3 turns NewsMap from a globe that everyone sees equally into a personal event radar. The product principle is: **rank and notify on event changes, never on raw article volume**.

## V3.0 — Event-change foundation

Status: implemented.

- Events transition through `ACTIVE`, `DORMANT`, and `ARCHIVED` states.
- A dormant event can reopen when a new report matches it.
- Durable updates record event creation, added reports, new sources, reopening, and lifecycle changes.
- Acceptance: rerunning without new articles creates no duplicate report updates; event IDs remain stable.

## V3.1 — Profiles and follows

Status: first local slice implemented.

- Persist a profile and follows for categories, locations, entities, sources, and individual events.
- Provide follow/unfollow APIs and a local profile used during development.
- Keep the storage contract independent from authentication.
- Acceptance: follows survive backend restarts and duplicate follows are idempotent.

Before public deployment, replace the local profile bridge with authenticated identities. Recommended order: passkeys or OAuth, secure server-side sessions, account deletion/export, then optional email/password. Do not put authorization logic in the React client.

## V3.2 — My Radar ranking

- Rank active events using explicit follows, freshness, event confidence, source diversity, and update momentum.
- Always return human-readable ranking reasons such as “Followed location: Ukraine”.
- Provide a discovery fallback when a new profile has no follows.
- Add negative feedback: mute event, mute entity, and “show less like this”.
- Acceptance: every ranked item has at least one inspectable reason; blocked/muted subjects never leak into results.

## V3.3 — Notification policy and inbox

Status: first in-app slice implemented.

- Convert durable event updates into notification candidates.
- Trigger for meaningful changes: event reopened, first independent source, major location/status change, or user-followed event update.
- Deduplicate and bundle related changes into one notification.
- Add quiet hours, urgency thresholds, per-follow frequency, and an in-app notification inbox.
- Acceptance: repeated articles about the same unchanged event do not notify; notification decisions are replayable and auditable.

Current implementation persists one deduplicated inbox item per user and event update. It ignores updates that predate the matching follow, alerts broad interests only for new events, reopening, or a newly independent source, and gives direct event follows finer status/report updates. Read and unread state survives backend restarts. Event changes carry `NORMAL` or `HIGH` priority; reopening and newly independent-source changes are high priority. Users can set each follow to all updates, important only, or muted, and can persist alert enablement, global minimum priority, quiet hours, and timezone from the inbox settings view.

Eligible changes now enter a durable outbox. One open digest bundle is maintained per user and event, repeated changes extend its five-minute aggregation window, global priority thresholds are applied before queueing, and quiet hours defer normal-priority bundles. High-priority bundles remain urgent. The next increment is a delivery worker with retries and channel-specific email or web-push adapters.

## V3.4 — Delivery channels

- Start with in-app notifications and daily briefing.
- Add email after unsubscribe, bounce handling, and rate controls exist.
- Add web push only after opt-in UX and per-device revocation are ready.
- Use an outbox table so delivery failures never lose notification intent. Status: durable intent and bundle storage implemented; delivery worker pending.
- Acceptance: retries are idempotent, user preferences are enforced at send time, and every delivery has an audit state.

## V3.5 — Learning and privacy

- Learn from opens, hides, follows, dwell, and notification dismissals without overriding explicit choices.
- Keep explicit follows as the strongest ranking signal.
- Explain personalization, offer history reset, and support account export/deletion.
- Measure event-level outcomes: useful event opens, follow retention, notification usefulness, and false-match reports—not clicks per article.

## Data boundaries

- Python owns semantic event identity, lifecycle, and update facts.
- Java owns users, follows, ranking policy, notification decisions, and delivery state.
- React owns presentation and user intent, never authorization.
- SQLite is appropriate for local development; migrate the same tables to PostgreSQL before multi-instance deployment.
