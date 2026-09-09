# NewsMap 🌍📰

**Interactive world map for discovering the latest and most important news by region.**

NewsMap is a full-stack application that turns fetched articles into persistent news events and visualizes them on an interactive world map. A Java Spring Boot backend serves the browser application, while a local Python pipeline creates embeddings and decides whether each new article belongs to an existing event or starts a new one.

## 🚀 Features

* **Interactive Map Interface**: Clickable world map to filter news by country or region.
* **Real-Time Headlines**: Fetches the latest news using open news APIs.
* **Persistent Events**: Assigns stable event IDs and lifecycle states that survive later fetch and matching runs.
* **My Radar**: Persists follows and ranks active events with inspectable personalization reasons.
* **Local Semantic Matching**: Combines embeddings, title overlap, entities, recency, and source evidence without requiring an LLM API key.
* **Offline Quality Analysis**: Retains HDBSCAN as a corpus-analysis tool rather than the live product identity model.
* **Region-Based Filtering**: Intuitively browse news specific to selected geographical areas.
* **Modern Tech Stack**: Built with Java Spring Boot for robust backend handling and Python for specialized ML tasks.

## 🛠️ Tech Stack

* **Backend**: Java
* **AI/ML Service**: Python (Embeddings, Text Mining)
* **Build Tool**: Maven
* **Frontend**: React (interactive 3D globe); the original JavaFX client remains during migration
* **Data**: General-purpose website crawler written in Java

## 📂 Project Structure

* **`src/main/newsmap/web/`**: Spring Boot API and pipeline scheduler.
* **`frontend/`**: React globe application.
* **`embeddings-service/`**: Local embedding, persistent-event matching, and offline clustering pipeline.
* **`configs/newsConfigs/`**: Configuration files for news sources and crawling settings.

## ⚙️ Prerequisites

Before you begin, ensure you have the following installed:

* **Java 21** or higher
* **Python 3.9** or higher
* **Maven** (or use the included `mvnw` wrapper)

No external AI API key is required for fetching, embedding, event matching, or clustering.

Event locations are resolved from explicit place evidence locally. To enable the optional fallback for ambiguous events, create a Gemini API key and expose it before starting the backend:

```bash
export GEMINI_API_KEY="your-key"
./mvnw spring-boot:run
```

`GOOGLE_API_KEY` is also accepted. The fallback model defaults to `gemini-2.5-flash-lite` and can be overridden with `GEMINI_MODEL`. Unresolved locations are not cached permanently, so restarting after adding a key retries them.

## Persistent event API

After running the pipeline and starting Spring Boot, the event projection is available at:

* `GET /api/events?status=ACTIVE&minArticles=1`
* `GET /api/events/{eventId}`

The canonical event state is stored in `embeddings-service/outputs/events.db`. The API reads its generated and geolocated `events.json` projection. Re-running the matcher is idempotent: already assigned articles are skipped and existing event IDs remain stable. Client-side spatial clustering changes only marker presentation; it never replaces or renumbers events.

Events move from `ACTIVE` to `DORMANT` and eventually `ARCHIVED` based on inactivity. Durable update records preserve creation, report additions, source additions, lifecycle changes, and reopening signals for later notification decisions.

## Local personalization API

V3 begins with a persistent local development profile:

* `GET /api/users/local`
* `POST /api/users/local/follows` with `{ "type": "CATEGORY", "value": "WAR" }`
* `DELETE /api/users/local/follows?type=CATEGORY&value=WAR`
* `PUT /api/users/local/follows/notification-level` with `{ "type": "CATEGORY", "value": "WAR", "notificationLevel": "IMPORTANT" }`
* `GET /api/radar?userId=local`
* `GET /api/notifications?userId=local`
* `POST /api/notifications/refresh?userId=local`
* `POST /api/notifications/{notificationId}/read?userId=local`
* `POST /api/notifications/read-all?userId=local`
* `GET /api/notifications/outbox?userId=local&status=PENDING`
* `GET /api/users/local/notification-preferences`
* `PUT /api/users/local/notification-preferences`

The preference model supports `CATEGORY`, `LOCATION`, `ENTITY`, `SOURCE`, and `EVENT` follows. The full delivery plan and deployment boundaries are documented in `docs/v3-personalization-roadmap.md`. Public deployment still requires authenticated server-side identities; the local profile is not an authentication substitute.

The in-app inbox is generated from durable event updates. Notification generation is idempotent, ignores event history from before a follow began, and stores read state in `data/personalization.db`. Broad follows alert on new events, reopening, and newly independent sources; direct event follows can also receive finer report and status updates. Each change is stored with `NORMAL` or `HIGH` priority. Individual follows support `ALL`, `IMPORTANT`, and `MUTED` notification levels. Delivery preferences persist alert enablement, minimum priority, quiet hours, and IANA timezone. They are deliberately separate from the inbox: disabling future push or email alerts does not erase meaningful event changes already recorded in the app.

Eligible notification changes are also written to a durable delivery outbox. Changes for the same user and event share one open digest bundle, with a five-minute aggregation window. New changes extend that bundle instead of creating delivery spam. Quiet hours postpone normal-priority bundles to the configured local end time; high-priority bundles bypass the quiet-hour delay. The outbox currently records delivery intent and retry metadata but does not send email or push notifications yet.
