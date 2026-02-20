# AI Coding & Testing Rules

This file defines the rules for development and testing in this project. These rules should be considered and extended in future changes.

## 0. General Rules
* **Language:** All texts in the project (code, comments, documentation, CLI output) must be in English.

## 1. Coding Rules

### Architecture & Design
* **Hexagonal Architecture:** The project follows the principle of hexagonal architecture (Ports & Adapters).
    * `domain`: Contains the core logic and models. Must not have dependencies on infrastructure details.
    * `application`: Contains the CLI logic and use cases.
    * `infrastructure`: Contains the concrete implementations (e.g., file system access, configuration).
* **Domain-Driven Design (DDD):** Use concepts like Aggregates, Entities, and Value Objects where appropriate (see `Repo`, `RepoFile`).
* **Immutability:** Prefer immutable data structures (e.g., Lombok `@Value`, `@Builder`).

### Error Handling
* **Result-Pattern:** Use the `Result<Success, Error>` class (from `paxel.lib`) instead of exceptions for expected error states.
* **Explicit Error Types:** Create specific error classes in the `domain.model.errors` package.

### Code Style
* **Lombok:** Use Lombok to minimize boilerplate code (`@Data`, `@Getter`, `@RequiredArgsConstructor`, etc.).
* **Clean Code:** Clear naming, small methods, Single Responsibility Principle.

## 2. Testing Rules

### Test Types
* **Unit Tests:** Every new service or process should be covered by unit tests.
* **Mocks/Stubs:** Use stubs or mocks for infrastructure dependencies (e.g., `FileSystem`, `DedupConfig`) to keep tests fast and isolated.
* **Integration Tests:** Use `@TempDir` to test real file system operations in an isolated environment.

### Test Frameworks
* **JUnit 5:** As the primary test framework.
* **AssertJ:** For fluent and readable assertions.

### Procedure
* **Regression Tests:** When fixing a bug, always create a test that reproduces the error.
* **Coverage:** Critical logic (such as the `UpdateReposProcess` or `IndexManager`) must have high test coverage.

## 3. CLI Development
* **Picocli:** Use Picocli for defining CLI commands and parameters.
* **Consistency:** New commands should fit into the existing structure (`repo`, `files`, `diff`).

## 4. Future Extensions
* (Future rules will be added here)
