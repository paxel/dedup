# dedup — content-addressed repository and duplicate management CLI

## SYNOPSIS
```
dedup [-v] <command> [<subcommand>] [options] [arguments]
```

Commands: `repo` | `files` | `diff`

## DESCRIPTION
`dedup` manages repositories that index files by content hash (content-addressed) to help you find duplicates, audit collections, and move/copy/remove files safely based on differences. It works across both repositories and plain directories and is designed for large photo/video/document libraries.

Subcommands are grouped by domain:
- `repo` — repository operations
- `files` — file operations within repositories or directories
- `diff` — differences between sources and references

### Purpose
- Keep one canonical copy of identical files across multiple folders or disks
- Detect and manage duplicates without relying on names, dates, or paths
- Stage and curate content (e.g., import from camera → triage folder → library)
- Generate repeatable file moves/copies based on deterministic hashes

### Common use cases
- Consolidate scattered media libraries: index multiple disks, find dupes, and de-duplicate.
- Mirror or backfill content: copy what’s missing from A to B, or remove already-backed-up items from an import folder.
- Clean staging areas: after sorting, remove files that already exist in your library.
- Inventory and analytics: list files, filter by patterns, and inspect MIME types.

## GLOBAL OPTIONS
- `-v` — Enable verbose logging. Inherited by all subcommands.

## COMMANDS

### repo — manipulate repositories

#### repo create
```
dedup repo create <name> <path> [-i <indices> | --indices <indices>] [--codec {json|messagepack}] [--strict]
```
Create a new repository named `name` at filesystem `path`.

Options:
- `-i, --indices` — Number of index files (default: 10).
- `--codec` — Persist the repo's line codec. Supported values: `json`, `messagepack`. If not specified, the repo is written with `messagepack` by default (backwards compatible: missing field reads as `json`).
- `--strict` — Fail if persisting the codec fails.

Examples:
- Create a library repo at `~/Libraries/Photos` with 16 index shards:
  ```
  dedup repo create photos ~/Libraries/Photos -i 16
  ```

#### repo rm
```
dedup repo rm <name>
```
Delete the repository `name`.

Example:
```
dedup repo rm old_temp_repo
```

#### repo ls
```
dedup repo ls
```
List all repositories.

Example:
```
dedup repo ls
```

#### repo update
```
dedup repo update [<name> ...] [-R <name> ...] [-a | --all] [-t <threads> | --threads <threads>] [--no-progress]
```
Read file changes from each specified repository's path and update its database. Repositories can be specified as positional arguments or using the `-R` option. If no names are given, combine with `--all` to update all repos. Use `threads` to control hashing concurrency (default: 2). Use `--no-progress` to suppress the progress display.

Examples:
- Update a single repo with 4 hashing threads:
  ```
  dedup repo update -R photos -t 4
  ```
- Update all repos without the progress UI (useful in CI):
  ```
  dedup repo update --all --no-progress
  ```

#### repo prune
```
dedup repo prune [<name> ...] [-R <name> ...] [-a | --all] [-i <indices> | --indices <indices>] [--keep-deleted] [--change-codec {json|messagepack}]
```
Prune the database by removing old versions and deleted files. Repositories can be specified as positional arguments or using the `-R` option. Options:
- `-i, --indices` — Number of index files in the output (default: 10).
- `--keep-deleted` — Keep entries marked as deleted (do not drop missing files).
- `--change-codec` — Change the repo codec while pruning. Accepts `json` or `messagepack` and writes the pruned result with the selected codec.

Example:
```
dedup repo prune -R photos -i 16
```

#### repo cp
```
dedup repo cp <sourceRepo> <destRepo> <path>
```
Copy repository `sourceRepo` into a new repository named `destRepo` at filesystem `path`. The original repository is not modified.

Use when you want a snapshot copy of your metadata under a new repo name/location.

Example:
```
dedup repo cp photos photos_backup /mnt/backup/Photos
```

#### repo rel
```
dedup repo rel <repo> <path>
```
Relocate the filesystem path of `repo` to `path` without changing existing entries.

Use after moving the underlying files to a new mount point or directory.

Example:
```
dedup repo rel photos /Volumes/Media/Photos
```

#### repo mv
```
dedup repo mv <sourceRepo> <destRepo>
```
Rename a repository from `sourceRepo` to `destRepo`. Entries and content remain unchanged; only the repo identity changes.

Example:
```
dedup repo mv staging photos
```

#### repo dupes
```
dedup repo dupes [<name> ...] [-R <name> ...] [-a | --all]
```
List and/or manage duplicates across the specified repositories or all repositories when `--all` is used. Repositories can be specified as positional arguments or using the `-R` option. The tool identifies duplicates by content hash.

Examples:
- See duplicates in one repo:
  ```
  dedup repo dupes -R photos
  ```
- Scan all repos for duplicates:
  ```
  dedup repo dupes --all
  ```

### files — manage files in repositories or directories

#### files ls
```
dedup files ls <repo> [-f <filter> | --filter <filter>]
```
List files in `repo` with an optional `filter` expression.

Examples:
- List all JPEGs in `photos`:
  ```
  dedup files ls photos --filter "ext:jpg"
  ```
- List files whose path contains "holiday":
  ```
  dedup files ls photos --filter holiday
  ```

#### files rm
```
dedup files rm <repo> [-f <filter> | --filter <filter>]
```
Delete files in `repo` matching the optional `filter`.

Example (dry run recommended via `-v` for visibility before running for real):
```
dedup -v files rm photos --filter "ext:tmp"
```

#### files cp
```
dedup files cp <source> <target> [--appendix <text>] [-f <filter> | --filter <filter>]
```
Copy files from `source` to `target` that match the optional `filter`. If `--appendix` is specified, `text` is appended to destination file names.

Use this to stage or export a subset from a repo or directory.

Examples:
- Export RAW photos to a review folder, appending `-review` to names:
  ```
  dedup files cp photos ~/Review --appendix -review --filter "ext:cr2"
  ```
- Copy only files under a certain subpath:
  ```
  dedup files cp photos ~/Export --filter "path:Family/2025"
  ```

#### files mv
```
dedup files mv <source> <target> [--appendix <text>] [-f <filter> | --filter <filter>]
```
Move (copy then remove) files from `source` to `target` that match the optional `filter`. If `--appendix` is specified, `text` is appended to destination file names.

Useful to reorganize content while keeping the move operations deterministic by hash.

Example:
```
dedup files mv photos ~/Archive/2025 --filter "year:2025"
```

#### files types
```
dedup files types <repo>
```
List MIME types discovered within `repo`.

Example:
```
dedup files types photos
```

### diff — compute/act on differences between a source and a reference

Both `source` and `reference` can be repositories or directories.

#### diff print
```
dedup diff print <source> <reference> [-f <filter> | --filter <filter>]
```
Print differences where `source` contains files not present in `reference` (optionally restricted by `filter`). Use this before `cp`/`mv` to verify which files would be affected.

Examples:
- Compare a staging folder against the main library (directories vs repo):
  ```
  dedup diff print ~/Downloads/Import photos --filter "ext:jpg"
  ```
- Compare two repos:
  ```
  dedup diff print staging photos
  ```

#### diff cp
```
dedup diff cp <source> <reference> <target> [-f <filter> | --filter <filter>]
```
Copy files that exist in `source` and not in `reference` into `target`.

Examples:
- Backfill missing items from `photos` to `backup` repo path:
  ```
  dedup diff cp photos backup /mnt/backup/Photos
  ```
- Copy only videos not yet in the library:
  ```
  dedup diff cp ~/CameraRoll photos ~/ToImport --filter "mime:video/*"
  ```

#### diff mv
```
dedup diff mv <source> <reference> <target> [-f <filter> | --filter <filter>]
```
Move files that exist in `source` and not in `reference` into `target`.

Example (clear staging folder after import to library):
```
dedup diff mv ~/Staging photos ~/Imported
```

#### diff rm
```
dedup diff rm <source> <reference> [-f <filter> | --filter <filter>]
```
Remove files from `source` that already exist in `reference` (optionally restricted by `filter`).

#### diff sync
```
dedup diff sync <source> <target> [--copyNew] [--deleteMissing] [--mirror] [-f <filter> | --filter <filter>]
```
Synchronize `target` repository with `source` based on content.
- `--copyNew` (default: true) — copy files present in `source` but missing in `target` (by content).
- `--deleteMissing` (default: false) — delete files in `target` that are marked missing in `source`.
- `--mirror` — shortcut for both of the above.

Example:
```
dedup diff sync sourceRepo targetRepo --mirror
```

Examples:
- Clean an import/downloads folder after verifying backup to `photos`:
  ```
  dedup diff rm ~/Downloads photos --filter "ext:jpg"
  ```
- Remove library duplicates from a scratch area regardless of path differences:
  ```
  dedup diff rm ~/Scratch photos
  ```

## FILTERS
Several subcommands accept `-f` / `--filter` to limit affected files. The filter syntax is project-specific; common patterns include:
- `ext:jpg` — file extension equals `jpg`
- `mime:video/*` — MIME type matches wildcard
- `path:Family/2025` — path contains the substring
- Plain substring (e.g., `holiday`) — matches file path/name

Combine filters by repeating `-f` if supported by your build, or pre-filter lists using `files ls` piped to your tooling. When in doubt, start with `diff print` to preview.

## EXAMPLES

Create a repository and index files using 4 threads:
```
dedup repo create photos ~/Pictures -i 16
dedup repo update -R photos -t 4
```

List duplicates across all repositories:
```
dedup repo dupes --all
```

Copy files present in source but not in reference into a target directory:
```
dedup diff cp photos backup ~/to_review --filter "ext:jpg"
```

List files of a repo with a name filter:
```
dedup files ls photos --filter holiday
```

Clean an import folder by removing items already present in your main library:
```
dedup diff rm ~/Import photos
```

Mirror only missing items from an external drive to your library:
```
dedup diff cp /media/card/DCIM photos ~/ToImport
```

## EXIT STATUS
`dedup` exits with status 0 on success and a non-zero value on error.

## SEE ALSO
- `docs/man/dedup.1` (roff man page)
- `README.md`
