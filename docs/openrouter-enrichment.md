# OpenRouter location and tag enrichment

This replaces the proposed Gemini web export/import workflow. It is an automated
server-side API integration. No browser automation or manual news uploads are used.
The implementation is ready; no live calls have been made because the user will
configure their OpenRouter credit and key later.

## Activate a small pilot

1. Add a small credit balance at https://openrouter.ai/settings/credits.
2. Create a dedicated NewsMap key at https://openrouter.ai/settings/keys. Set a
   $5 limit with no reset. This is the provider-side stop for that key; account
   top-up fees and other applications are separate.
3. Add the key after `OPENROUTER_API_KEY=` in `deploy/openrouter.env.local`.
   The file is ignored by Git, excluded from Docker build context, and created
   with private file permissions. Never put the key in a frontend/VITE variable,
   paste it into chat, or commit it. On a new host copy `deploy/openrouter.env.example`
   to `deploy/openrouter.env.local` and set permissions to owner-read/write only.
4. The local file initially limits each pass to five new AI calls. Build the backend,
   then run the one-shot enrichment of existing events from the repository root:

```sh
./mvnw package spring-boot:repackage
python3 deploy/run_enrichment.py
```

Stop any running backend before using the one-shot helper, so two processes do not write the event projection concurrently.

The helper launches only enrichment, without crawling news or starting the web
server. It passes credentials in the process environment, not command arguments.
It reads the private file as configuration data; it does not execute shell code.
Review the five results before raising `NEWSMAP_ENRICHMENT_MAX_EVENTS_PER_RUN` to 25.
Country-level pins are explicitly marked as country-level. Global and uncertain
stories are not assigned a false precise point.

The hosted preview does not change automatically. After reviewing local results,
export a refreshed preview and deploy its static output, as in the previous refresh.
The always-on backend uses the integration during its normal pipeline runs when
`OPENROUTER_API_KEY` is present. Compose loads the optional private file. Recreate
the backend container after changing it; Docker image rebuilding is unnecessary
for key changes alone. Native local launches must export the variables into the
backend's environment; the one-shot helper handles this for its own process.

## Defaults and spending controls

- Model: `deepseek/deepseek-v3.2`, configurable with `NEWSMAP_ENRICHMENT_MODEL`.
- Reasoning disabled, one structured request per event, no tools or web searches.
- Up to four recent report titles and bounded excerpts; output capped at 700 tokens.
- Structured-output-capable providers only, sorted by price. Request price ceilings:
  $0.30 per million input tokens, $0.60 per million output tokens, no per-request fee.
  Changing the model can produce no eligible provider rather than silently spending
  more. No fallback to a different, expensive model is configured.
- `NEWSMAP_ENRICHMENT_BUDGET_USD=5` is a lifetime application budget across restarts,
  recorded in `data/enrichment.db`. It is not a monthly allowance. Keep this database
  when backing up or moving servers; deleting it resets the local accounting guard.
- Before a request, reserve a conservative cost from UTF-8 request length, overhead,
  output cap and provider price ceilings. Actual reported usage cost settles the
  reservation; missing cost/timeouts retain the full reservation. The provider's
  key limit is the additional remote control, since local token/cost accounting
  cannot guarantee provider billing behavior. Cached results do not make new calls.
- An HTTP error, invalid response or exhausted local budget stops new calls for
  that pass. Cached results and local geotagging remain available. There is no
  retry loop that can continuously burn credit. A later pipeline pass may retry.

## Quality and persistence

Only ACTIVE events with reporting in the last 48 real hours are candidates for
paid enrichment; unresolved locations are processed first. The cache key includes
model, prompt version, event ID and selected reporting. Changed evidence triggers
reprocessing; identical evidence reuses the cache.

The response contains category, up to five topic tags, location scope, country
code, place name and article-linked evidence. Schema, allowed categories, tag
format, article IDs and literal evidence quotations are checked in Java. A mapped
place must occur in its cited evidence. These checks reject unsupported output;
they do not prove the model understood every geographic relationship correctly.
Review event location versus merely mentioned actors in the pilot.

Coordinates come from the checked-in Natural Earth country label points and a small
explicit city catalog. Unknown cities fall back to their country record with a
COUNTRY scope. More city coverage can be added later. AI never supplies coordinates,
and the UI does not invent a location-confidence percentage for AI assignments.

Future crawls retain up to 800 characters of article excerpt in event storage and
embedding metadata for this task. Existing historical reports without excerpts
can only provide headlines; the model is told not to invent missing evidence.
The API DTOs, saved-story snapshots, map preview and sidebar support the new tags,
evidence and location scope. The canonical Python event matcher still owns stable
event identities. AI enrichment is optional and does not change clustering IDs.

Gemini fallback calls are disabled whenever OpenRouter is configured, so this
integration does not accidentally charge two AI providers. The OpenRouter key is
removed from embedding subprocess environments, which do not need it.

## References

- https://openrouter.ai/deepseek/deepseek-v3.2
- https://openrouter.ai/docs/guides/features/structured-outputs
- https://openrouter.ai/docs/guides/routing/provider-selection
- https://openrouter.ai/docs/guides/best-practices/reasoning-tokens
