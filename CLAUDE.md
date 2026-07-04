# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Dedup treats directories as "repositories" of files and finds/processes duplicates within and across them. It is a Java 21 Maven project (`paxel.dedup`) with a Picocli CLI, an embedded Javalin web server, and a React 19 + Vite + Tailwind frontend in `ui/`.

Detailed project rules live in `ai/guidelines.md` — read it before making changes. `README.md` documents all CLI commands and the repo/index-file concepts.

## Commands

### Build & run
- `mvn clean package` — full build; also installs Node, runs `npm install`/`npm run build` in `ui/` (frontend-maven-plugin), bundles the UI into the jar, and produces `target/Dedup.jar`.
- `java -jar target/Dedup.jar` — run the CLI (subcommands: `repo`, `files`, `diff`).
- `java -jar target/Dedup.jar --ui` — start the web UI (default port 8080).

### Tests
- `mvn test` — run all tests.
- `mvn test -Dtest=IndexManagerTest` — single test class.
- `mvn test -Dtest=IndexManagerTest#methodName` — single test method.

### Frontend (in `ui/`)
- `npm run dev` — Vite dev server; proxies `/api` and `/events` (WebSocket) to `localhost:8080`, so start the Java backend with `--ui` alongside it.
- `npm run build` — typecheck (`tsc`) + production build to `ui/dist`.
- `npm run lint` — ESLint, zero warnings allowed.

## Architecture

Hexagonal (ports & adapters) under `src/main/java/paxel/dedup/`:

- `domain/` — pure core logic; no infrastructure dependencies.
  - `domain/model/` — entities (`Repo`, `RepoFile`), hashing (`Sha1Hasher`), similarity fingerprinters (image/video/audio/PDF), `Filter`/`FilterFactory`, `ResilientFileWalker`.
  - `domain/port/out/` — ports: `FileSystem`, `LineCodec`.
  - `domain/service/` — `RepoService` (backend facade used by the web layer), `EventBus`, observer interfaces (`DupeObserver`, `UpdateObserver` in model).
- `repo/domain/` — the use-case "processes", one class per CLI operation: `repo/` (`CreateRepoProcess`, `UpdateReposProcess`, `PruneReposProcess`, `DuplicateRepoProcess`, ...), `diff/DiffProcess`, `files/FilesProcess`. `RepoManager`/`IndexManager` own repo state and the index files.
- `application/cli/` — `DedupCli` (main class) and Picocli command definitions in `parameter/`.
- `infrastructure/` — adapters and config:
  - `adapter/in/web/UiServer.java` — all Javalin REST routes (`/api/...`) and the `/events` WebSocket; serves the built frontend from classpath `static/`.
  - `adapter/out/filesystem/NioFileSystemAdapter` — the real `FileSystem` implementation.
  - `adapter/out/serialization/` — index-file line codecs (JSON and MessagePack frame readers/writers).
  - `adapter/out/web/`, `adapter/out/terminal/` — observer implementations that stream progress to the WebSocket or the ANSI terminal.
  - `config/` — `DedupConfig` (config dir is `~/.config/dedup/`), `InfrastructureConfig` (manual dependency wiring, no DI framework).

### Key concepts
- A repo's config lives at `~/.config/dedup/repos/<name>/dedup_repo.yml` plus N append-only `.idx` index files, sharded by `filesize % N`. Index entries are append-only logs: the last entry for a path wins; pruning rewrites them (via `.bak` files). Duplicate lookup is always size-first, then hash.
- Business-logic failures use `paxel.lib.Result<Success, Error>` (from the `tool-shed` dependency), not exceptions.
- Progress flows through observer interfaces; the web adapters publish to the `EventBus`, which streams over WebSocket. Throttle WebSocket updates to ~5Hz.

## Code style (from ai/guidelines.md)

- English only in code, comments, and output.
- Use Lombok (`@Value`, `@Data`, `@Builder`, `@RequiredArgsConstructor`) to minimize boilerplate; prefer immutable models.
- No ternary operators — use explicit `if/else`.
- No cross-cutting utility classes (e.g. `StringUtil`); prefer instance methods over static ones (exceptions: constants, entry points, factories, enums).

## Testing rules

- JUnit 5 + AssertJ + Mockito + Awaitility.
- Unit tests are mandatory for new services/processes. Do NOT do real filesystem or network I/O in unit tests — mock the `FileSystem` port. `@TempDir` is reserved for explicit end-to-end integration tests.
- Use precise assertions (`containsExactly...`, specific field checks), not just `isNotNull()`.
- When fixing a bug, add a test reproducing it.
