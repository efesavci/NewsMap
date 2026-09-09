# AGENTS.md

## Core behavior

- Understand the existing project before coding.
- Preserve the current structure and architecture when possible.
- Do not silently redesign the project.
- Prefer incremental migration over rewrites.
- Make the smallest useful change.
- Do not overengineer.
- Touch only files related to the task.
- Verify changes before claiming success.

---

## Project goal

NewsMap is currently a local Java/JavaFX application.

The goal is to convert it into a deployable web application while preserving as much existing logic and structure as possible.

Target direction:
- backend remains mostly Java
- browser-based frontend
- runs on localhost
- deployable later

---

## Migration strategy

Preferred order:

1. understand current structure
2. identify reusable logic
3. separate JavaFX-specific code
4. expose HTTP API
5. add browser frontend
6. make deployable

Avoid large rewrites unless necessary.

Every migration step should keep the project runnable.

---

## Technical preferences

Frontend:
- React or Next.js

Backend:
- Spring Boot or lightweight Java API

Database:
- SQLite or PostgreSQL

Keep architecture simple.

Do not introduce:
- microservices
- Kubernetes
- Kafka
- Redis
- unnecessary abstractions

---

## Before coding

Always:
1. inspect the structure
2. explain findings briefly
3. propose the smallest next step

---

## Verification

Before finishing:
- run relevant builds/tests
- verify localhost functionality
- summarize changed files
- explain next recommended step