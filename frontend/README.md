# React + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and [`typescript-eslint`](https://typescript-eslint.io) in your project.

## Shareable feedback preview

The feedback preview uses the existing UI with an isolated browser-local API adapter.
Build with `VITE_PREVIEW_MODE=true npm run build`, then deploy `dist/` as a static
Vercel project (no install/build command required for this prebuilt output).
The dedicated project is `newsmap-preview` in the `supdewdz` team.

`public/preview/events.json` is generated from the local event projection by
`python3 frontend/scripts/export-events-preview.py`. It includes active events
with geolocation and reporting from the past 48 hours, preserving original dates.
The 5 November 2026 refresh contains 22 mapped events and 33 reports in that window.
The UI displays the snapshot timestamp from the file.

This static snapshot does not crawl or refresh itself. Guest follows, notification
settings, event-match reviews, saved news and archives are stored only in this
browser's local storage. Reviews are not sent to the owner. No accounts, passwords,
live pipeline health or notification deliveries are connected. Sign out resets
local guest choices but preserves saved news. Saved and Archive folders have no
expiry; clearing browser data removes preview saves. Normal builds omit the
preview adapter and retain the real backend API behavior.

Shared URL: https://newsmap-supdewdz.vercel.app/

Checked on 5 November 2026: preview build, ESLint, browser onboarding, globe,
event details, following an event into My Radar, and page reload.
