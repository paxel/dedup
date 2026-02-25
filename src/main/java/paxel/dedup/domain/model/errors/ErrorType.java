package paxel.dedup.domain.model.errors;

/**
 * Canonical list of error categories in the application. This replaces the many specific
 * error record types we previously had (e.g., CreateRepoError, OpenRepoError, ...).
 */
public enum ErrorType {
    // Repository configuration and CRUD
    OPEN_REPO,
    CREATE_REPO,
    MODIFY_REPO,
    DELETE_REPO,
    RENAME_REPO,

    // Repository processing
    LOAD,
    WRITE,
    CLOSE,
    UPDATE_REPO,

    // IO and configuration
    IO,
    CONFIG,

    // Generic fallback
    GENERAL
}
