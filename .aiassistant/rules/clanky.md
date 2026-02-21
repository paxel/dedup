---
apply: always
---

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
* **Avoid Primitive Obsession:** Use enums/value objects instead of ad-hoc strings/ints for domain choices (e.g., `Repo.Codec` instead of string codec names). For config map keys, avoid raw string literals; centralize keys using a small enum colocated with the mapping code (no standalone constants/utility classes).
* **Not Invented Here (NIH):** Prefer proven, well-maintained libraries over custom implementations for standard concerns (e.g., JSON/YAML parsing, serialization, collections, concurrency). Do NOT reimplement parsers/serializers or common utilities when a solid library exists, unless there is a compelling project-specific constraint (document it explicitly).
* **Keep It Lean:** Avoid introducing unnecessary statistics helpers or generic utility classes; prefer focused, cohesive methods close to where they are used.

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

### Meaningful Assertions
* Tests must assert specific, meaningful outcomes — avoid assertions that only check non-nullity or emptiness when richer expectations are known.
    * Prefer exact values over existence checks (e.g., file counts, hash strings, sizes, flags like `missing`, ordering of results).
    * Validate side effects and persisted artifacts (e.g., index file names created, file contents/lines written, JSON fields) rather than just success codes.
    * When asserting collections, check size and relevant contents (contains exactly, order when important) instead of `isNotEmpty()`.
    * For errors, assert the precise error type and key fields/messages, not just that an operation "failed".
    * Cover at least one positive path and one negative/edge case per public behavior when feasible.
* Example (before → after):
    * Before: `assertThat(stats).isNotNull();`
    * After:
        - `assertThat(stats.get("files")).isEqualTo(1L);`
        - `assertThat(indexPath).exists();`
        - `assertThat(Files.readAllLines(indexPath)).singleElement().contains("file1.txt");`
* Use AssertJ features:
    * `containsExactly`, `containsOnly`, `containsExactlyInAnyOrder`, `singleElement`, `extracting`, `usingRecursiveComparison()` for nested objects.
* Test data must be deterministic. Avoid time- and randomness-based flakiness; inject clocks/executors or fix seeds where needed.

## 3. CLI Development
* **Picocli:** Use Picocli for defining CLI commands and parameters.
* **Consistency:** New commands should fit into the existing structure (`repo`, `files`, `diff`).
* **Maven Sync Reminder:** Whenever `pom.xml` is modified, explicitly remind the user to click "Sync Maven" in the IDE. This reminder must be included in the PR/commit message and in the assistant's response after any `pom.xml` change.

## 4. Future Extensions
* (Future rules will be added here)
