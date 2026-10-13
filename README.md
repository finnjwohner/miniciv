# miniciv

**Civilization for your lunch break.** Two to four friends, one browser tab each, and a race to be the last Nation standing. No hexes to push and no units to march across a map. You make the big decisions and read about how they went.

Play it at **[miniciv.com](https://miniciv.com)**.

## How a Game goes

Someone makes a Room and shares its three-word code (or an invite link). Everyone names their Nation and Ruler, picks a colour and an emblem, and readies up. Then the Turns begin.

Everyone acts **at the same time**. A Turn lasts at most two minutes, and usually far less. In that time you might:

- 🌾 **Grow.** Put Gold into Farms, Markets, Libraries and Banks. Raise Palisades, Walls, Moats and a Castle to keep your people safe.
- 📜 **Research.** Climb a Tech Tree you can see only one step ahead of.
- ⚔️ **Fight.** Train Warriors, Archers and Horsemen, which beat each other rock-paper-scissors style, and later Crossbowmen, Knights and Catapults. Send them off as a Warparty and hope your rivals haven't seen it coming.
- 🕵️ **Scheme.** Send Scouts into the wilds to find other Nations. Slip Spies into a rival's capital to gather intel, sabotage, poison, incite unrest or steal, then try to get the Haul home without being Unmasked.
- ✉️ **Negotiate.** Send private Messages and offer Tribute, and make promises you may or may not keep.

When the Turn ends, everything resolves at once, and you get a **Turn Report**: a short story of what happened. Your Warparty broke through their Walls and came home with Plunder. A comet blazed across the sky and your astronomers learned from it. Fire took the scrolls of your Library. One of your Spies was caught and confessed, and now *they* know where you are.

The world won't leave you alone either. **Events** strike out of nowhere, a **Dilemma** may ask you to make a hard call, and your Warparties can run into other armies on the road. From about Turn 8, **Barbarian Camps** start appearing in the wilds and raiding anyone with too small an Army. Find a Camp and burn it down for a Bounty, or keep your Walls high and hope they pick on someone else.

A Nation is conquered when it runs out of both Population and Army. The last one standing wins. Then everyone hits **Play again**.

The game's vocabulary is defined in [`GLOSSARY.md`](GLOSSARY.md), and key design decisions are recorded in [`docs/adr/`](docs/adr/).

# Development

miniciv is built with Spring Boot and React. All game rules live on the server, and the ruleset (Units, Buildings, Technologies, Events, Missions and Barbarians) is plain JSON in `backend/src/main/resources/ruleset/`. Hosting is described in [`docs/hosting.md`](docs/hosting.md).

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
cd backend && ./mvnw test                                   # all backend and frontend tests (builds the frontend first)
cd backend && ./mvnw test -Dskip.npm -Dskip.installnodenpm  # skip the frontend build and its tests
cd backend && ./mvnw test -Dtest=HealthEndpointTest         # a single backend test class, plus all frontend tests
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
