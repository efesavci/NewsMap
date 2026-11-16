# NewsMap hosting setup

The frontend is already at https://newsmap-supdewdz.vercel.app/.
The backend package is ready for a single always-on Linux Docker host. No server
has been purchased or provisioned, and live fetching is not active on Vercel yet.
The current website remains the dated feedback preview with browser-local saves (refreshed on 5 November 2026).

## What this deploys

- Java 21 API, account sessions, SQLite profiles and saved-story libraries.
- CPU Python news pipeline, scheduled every two hours at 00:00, 02:00, …, 22:00 UTC.
- Persistent volumes for profiles/crawl history/pending batches, event state, and downloaded models.
- Optional Caddy reverse proxy with automatic HTTPS and persistent certificates.
- Web health check, restart policy, bounded container logs, and a private backup command.

This is a single-instance setup. Do not scale the backend to multiple replicas:
SQLite, local event files, sessions and the scheduler are shared within this host.
It must stay awake for the schedule to run. Missed runs are not replayed. The first
pipeline run downloads the embedding model; benchmark its RAM, disk and run time
before choosing a long-term server size. This is not a lightweight static website.

## 1. Prepare the server

Use a host with Docker Engine and the Docker Compose plugin. Copy or clone this
repository there. Give the backend either a provider-issued HTTPS hostname, or a
hostname you own such as `api.your-domain.com`. The frontend's `vercel.app` address
stays with Vercel; it cannot also be the backend server's DNS address.

For the included Caddy proxy, point the backend hostname's DNS A record at the
server's public IPv4 address. Only add an AAAA record if IPv6 reaches that server.
Allow inbound TCP ports 80 and 443; allow SSH only as needed for administration.
The Java port 8080 remains bound to host loopback, not to the public Internet.

From the repository root, create your local configuration:

```sh
cp deploy/.env.example deploy/.env
```

Edit `deploy/.env`: enter the real backend hostname, the exact frontend origin,
and your certificate renewal email. Do not commit this file. It contains no API
keys by default; the news pipeline does not require an external AI key.

## 2. Start the backend

With the included HTTPS proxy:

```sh
docker compose --env-file deploy/.env -f deploy/compose.yaml --profile public config --quiet
docker compose --env-file deploy/.env -f deploy/compose.yaml --profile public up -d --build
docker compose --env-file deploy/.env -f deploy/compose.yaml logs -f backend
```

Caddy obtains and renews the certificate for the hostname once DNS and inbound
ports are ready. Configuration follows [Caddy's reverse-proxy documentation](https://caddyserver.com/docs/quick-starts/reverse-proxy)
and [automatic HTTPS guidance](https://caddyserver.com/docs/automatic-https).

If your hosting provider already supplies HTTPS termination, omit `--profile public`
and connect its proxy to the backend instead. Keep the configured frontend origin
and secure cookies; never expose an unencrypted public authentication endpoint.

Check the real hostname:

```sh
curl --fail https://YOUR-BACKEND-HOST/api/health
```

Expected: `{"status":"UP"}`. This confirms the API is ready, not that a news fetch
has finished. Check pipeline run history after signing in. A first deployment with
empty data starts gathering news automatically. Later deployments reuse stored
state. The Spring scheduler runs inside this backend; do not add a second cron job
for the same pipeline.

## 3. Connect Vercel after backend checks pass

Generate the frontend configuration with the real HTTPS origin:

```sh
python3 deploy/configure_frontend.py https://YOUR-BACKEND-HOST
```

This creates `frontend/vercel.json` with the Vite build, an `/api/*` proxy rewrite,
and `private, no-store` API responses. The generator does not deploy anything.
It rejects insecure origins, credentials, and URLs with paths or query strings.

For the existing Vercel project, switch from its current prebuilt static upload to
a **frontend source deployment**: use `frontend/` as the project root, Vite as the
framework, `npm ci` for install, `npm run build` for build, and `dist` for output.
Set `VITE_PREVIEW_MODE=false`. Include the generated `vercel.json` with this source
or at the root of a prebuilt static upload. A Java/Python backend is not bundled
into the frontend upload.

Requests stay under `https://newsmap-supdewdz.vercel.app/api/...`; account cookies
have no explicit domain and remain on that frontend origin. The backend's explicit
`NEWSMAP_FRONTEND_ORIGIN` permits the forwarded browser Origin header. Both CSRF
headers and PATCH preflights are allowed, with authentication and CSRF checks still
required. No wildcard credentialed origin is configured.

Do not switch the shared site out of preview mode until login, CSRF, saved news,
and pipeline behavior have been checked through the real HTTPS proxy.

## News retention and profiles

World and Radar show reporting in the last 48 real hours. The browser refreshes
the feed every minute. The slider narrows that window; it never deletes data.
Failed embedding batches are retained under `data/article-batches/` and retried; completed embedding checkpoints are reused. Routine fetching does not run UMAP/HDBSCAN.

Canonical crawl URLs and event IDs persist between runs, so new reports can join
existing events instead of replacing prior coverage.

Save story stores a full event/report snapshot for the authenticated user. Saved
and Archive folders have no expiry. Archive moves only that user's saved copy;
it does not remove an event for other people. Unsave removes only that copy.
Saved snapshots do not acquire later reports; following an event tracks future
updates separately. Saved stories remain after server restarts; users may need to
sign in again because login sessions are held in server memory.

## Backups and recovery

From the repository root:

```sh
./deploy/backup.sh
```

The script briefly stops the backend to prevent writes, archives both state
volumes, then restarts the backend if it was running. It does not stop Caddy.
The output is a mode-private file under `backups/`, ignored by Git. Optional first
argument: another backup directory. Model files are omitted because they can be
downloaded again. The archive contains profile details and password hashes: keep
it private, copy it securely off the server, and verify recovery before relying on it.
The helper is provided but has not been exercised against a Docker daemon here.

To recover on a replacement host: create the Compose volumes, keep the backend
stopped, and restore the archive's `data/` and `embeddings-service/outputs/` trees
into the matching volumes as UID/GID 10001. Start the backend and verify account
login, saved stories and event IDs before routing public traffic to it. Restore
only a backup you trust. Never use `docker compose down -v` for a routine update:
that removes persistent volumes. Keep the previous application image for rollback;
restore state from backup if a future release changes its schema incompatibly.

## Verification completed on 5 November 2026

- Backend tests: 3 passed; persistence, profile ownership and the 48-hour cutoff.
- Executable Java package: built and launched with isolated temporary storage.
- HTTP checks: health, exact production-origin CORS/PATCH preflight, signup/login
  session, CSRF enforcement, owner isolation, saving/archiving an old event while
  that event is absent from the live 48-hour feed.
- Restart check: signed back in and read the archived full story from persisted storage.
- Docker Compose configuration, backup shell syntax and proxy generator: checked.
- Full Docker image, Caddy certificate issuance, real-host news pipeline and final
  Vercel proxy: not tested. Docker is not running locally and no server is connected.

## Final release checks on the host

1. Confirm HTTPS health and account login through the final Vercel hostname.
2. Use two accounts to confirm separate libraries and denied cross-profile access.
3. Save and archive a story, restart the backend, sign in again, and read the copy.
4. Observe one scheduled run and the next incremental run: stable event IDs,
   skipped known URLs, new reports, and a completed run in pipeline history.
5. Confirm old reporting disappears after 48 hours while saved copies remain.
6. Take a backup and recover it on a separate test host or volume before launch.
