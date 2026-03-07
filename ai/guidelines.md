# Project Development Guidelines

This document provides relevant details for future development and debugging of the Dedup project. It extends the initial instruction set from `clanky.md`.

## 1. Build and Configuration Instructions

The project is built using **Maven** and targets **Java 21**.

### 1.1 Prerequisites
- **JDK 21** or later.
- **Maven 3.8+**.

### 1.2 Building the Project
To compile the project and build the executable JAR:
```bash
mvn clean package
```
This generates a standalone JAR with dependencies at `target/Dedup.jar`.

### 1.3 Running the Application
You can run the CLI directly using `java -jar`:
```bash
java -jar target/Dedup.jar --help
```

### 1.4 IDE Configuration (IntelliJ IDEA)
- **Lombok**: Ensure the Lombok plugin is installed and "Annotation Processing" is enabled in settings.
- **Maven Sync**: Whenever `pom.xml` is modified, click "Reload All Maven Projects" (Sync Maven). This is critical for updating dependencies and annotation processors (Picocli codegen, Lombok).

## 2. Testing Information

### 2.1 Test Frameworks
- **JUnit 5**: Primary test runner.
- **AssertJ**: Fluent assertions (preferred over JUnit built-in assertions).
- **Mockito**: Mocking and stubbing, especially for I/O operations.
- **Awaitility**: For testing asynchronous or eventually consistent behavior.

### 2.2 Running Tests
Execute all tests:
```bash
mvn test
```
Execute a specific test class:
```bash
mvn test -Dtest=IndexManagerTest
```

### 2.3 Guidelines for Adding Tests
- **Unit Tests**: Mandatory for every new service or process.
- **Mocks for I/O**: Do NOT perform real filesystem or network I/O in unit tests. Mock the `FileSystem` or relevant adapters.
- **AssertJ Assertions**: Use meaningful, specific assertions.
    - Prefer `containsExactly` or `containsExactlyInAnyOrder` for collections.
    - Check specific fields of results rather than just `isNotNull()`.
- **Reproducer Tests**: Always include a test that reproduces a bug when fixing it.
- **TempDir**: Use JUnit 5 `@TempDir` for integration tests that require a real filesystem scratch space.

### 2.4 Test Example
Here is a template for a standard unit test in this project:
```java
package paxel.dedup;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ExampleTest {
    @Test
    void shouldFollowProjectStyle() {
        // Arrange
        String input = "dedup";
        // Act
        String result = input.toUpperCase();
        // Assert
        assertThat(result)
            .as("Demonstrating AssertJ features")
            .isEqualTo("DEDUP")
            .hasSize(5);
    }
}
```

## 3. Additional Development Information

### 3.1 Architecture & Design
- **Hexagonal Architecture**: Keep the `domain` pure. Infrastructure concerns (FS, Serialization) stay in `infrastructure`.
- **DDD**: Use the defined `Repo` and `RepoFile` entities. Use `paxel.lib.Result` for error handling instead of exceptions for business logic failures.
- **Immutability**: Use Lombok `@Value` and `@Builder` for domain models.

### 3.2 Code Style
- **No Deep Nesting**: Use guard clauses and early returns.
- **No Ternary Operators**: Use explicit `if/else` or colocated helper methods for clarity.
- **Lombok usage**: Use `@Data`, `@Getter`, `@RequiredArgsConstructor` etc., to keep the code lean.
- **English Only**: No German comments or naming.

### 3.3 CLI Development
- Use **Picocli** for all CLI commands.
- Ensure new commands fit the `repo`, `files`, or `diff` sub-command structure.

---
*Last Updated: 2026-02-23*
