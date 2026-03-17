# Project Development & AI Guidelines

This document provides essential instructions for building, testing, and developing the Dedup project. It consolidates rules from `clanky.md` and `guidelines.md`.

## 1. General Rules
- **Language**: All code, comments, documentation, and CLI output must be in **English only**. No German.
- **Modern Java**: Use Java 21+ features and APIs.
- **Strict Adherence**: Follow instructions explicitly. Ask for confirmation if unsure.
- **Lombok**: Use `@Data`, `@Getter`, `@Value`, `@Builder`, and `@RequiredArgsConstructor` to minimize boilerplate.

## 2. Architecture & Design
- **Hexagonal Architecture (Ports & Adapters)**:
    - `domain`: Pure core logic and models. No infrastructure dependencies.
    - `application`: CLI logic, command handling, and use cases.
    - `infrastructure`: Concrete implementations (FS, Web, Serialization).
- **Domain-Driven Design (DDD)**: Use `Repo` and `RepoFile` as core entities.
- **Immutability**: Prefer immutable models (Lombok `@Value`).
- **Error Handling**: Use `paxel.lib.Result<Success, Error>` instead of exceptions for business logic failures.
- **No Utility Classes**: Do NOT add cross-cutting utility classes like `StringUtil`. Use private/static methods within the class or co-located helpers.
- **Avoid Static Methods**: Prefer instance methods. Exceptions: constants, entry points, factories, enums.
- **No Ternary Operators**: Use explicit `if/else` or co-located helper methods for clarity.

## 3. Build & Configuration
- **Tooling**: Maven 3.8+, JDK 21.
- **Building**: `mvn clean package` (generates `target/Dedup.jar`).
- **Maven Sync**: Always click "Sync Maven" in the IDE after `pom.xml` changes.
- **IDE Configuration**: Enable "Annotation Processing" for Lombok and Picocli.

## 4. Testing Rules
- **Frameworks**: JUnit 5 (Runner), AssertJ (Assertions), Mockito (Mocking), Awaitility (Async).
- **Unit Tests**: Mandatory for every new service or process.
- **Mocking I/O**: Do NOT perform real filesystem or network I/O in unit tests. Mock `FileSystem` and verify interactions.
- **Integration Tests**: Use `@TempDir` only for explicit end-to-end integration tests.
- **Meaningful Assertions**:
    - Avoid just `isNotNull()`. Check specific fields, file counts, and contents.
    - Use `containsExactly` or `containsExactlyInAnyOrder` for collections.
- **Reproducer Tests**: Always include a test that reproduces a bug when fixing it.

## 5. CLI & Web Development
- **CLI**: Use **Picocli** for all commands (`repo`, `files`, `diff`).
- **Web Interface**: Uses **Javalin 6.x** for the backend and **React 19** for the frontend.
- **Events**: Use the `EventBus` to stream progress updates via WebSockets.
- **Throttling**: WebSocket updates should be throttled to avoid UI flooding (target ~5Hz).

---
*Last Updated: 2026-03-17*
