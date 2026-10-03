# miniciv

A turn-based, player-vs-player strategy game in the browser for 2–4 Players. See [`GLOSSARY.md`](GLOSSARY.md) for the game's vocabulary and [`docs/adr/`](docs/adr/) for key decisions.

## Layout

- `backend/` — Spring Boot server (Java 21, Maven). Owns all game rules and serves the built frontend.
- `frontend/` — React + TypeScript single-page app (Vite).

## Requirements

- A JDK, version 21 or newer.
- Nothing else for a full build: the Maven wrapper downloads Maven, and the build downloads its own Node into `backend/target`.
- Node 22 (with npm) only if you want to run the frontend dev server yourself.

## Build and run

From `backend/`:

```sh
./mvnw clean package          # builds the frontend, runs all tests, produces the jar
java -jar target/miniciv-0.0.1-SNAPSHOT.jar
```

Then open http://localhost:8080. On Windows use `mvnw.cmd` instead of `./mvnw`.

## Develop

Run the backend and the frontend dev server side by side for hot reloading:

```sh
cd backend && ./mvnw spring-boot:run -Dskip.npm -Dskip.installnodenpm
cd frontend && npm install && npm run dev
```

Open http://localhost:5173. The dev server forwards `/api` and `/ws` to the backend on port 8080.

## Test

```sh
cd backend && ./mvnw test                                   # all backend tests (builds the frontend first)
cd backend && ./mvnw test -Dskip.npm -Dskip.installnodenpm  # skip the frontend build
cd backend && ./mvnw test -Dtest=HealthEndpointTest         # a single test class
```

When skipping the frontend build, `FrontendServingTest` only passes if `frontend/dist` has already been built.

Frontend checks, in `frontend/`:

```sh
npm test                                   # all frontend tests (Vitest + Testing Library)
npm test -- src/game/GameScreen.test.tsx   # a single test file
npm run build                              # type-checks and builds
npm run lint
```

Frontend tests render screens and check what a Player sees. Start from the Game in progress in `src/test/fixtures.tsx`, adjusting it per test, e.g. `renderGame({ nation: nationView({ dilemmas: [] }) })`.
