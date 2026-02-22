# Saturday Plan

This document outlines the Saturday work plan to evolve Dedup in four areas: pluggable line codecs with MessagePack support, a new `dupes` similarity command, and richer comparison strategies beyond plain hashes.

## 1) Add LineCodec: MessagePack, with repo-level codec selection (Completed)

- Abstractions ✓
  - Introduced `LineCodec` SPI and `JacksonMapperLineCodec`. ✓
  - Replaced redundant `JsonLineCodec` with `JacksonMapperLineCodec`. ✓
- Repo-level codec selection ✓
  - `repo create --codec messagepack` (now default). ✓
  - Persist codec in `dedup_repo.yml`. ✓
- Migration ✓
  - `repo prune --change-codec <codec>` allows migrating existing repos. ✓

## 2) Similarity detection and advanced filtering (Completed)

- Advanced size filtering ✓
  - Support for `>`, `<`, `>=`, `<=`, and `=`. ✓
- Image fingerprinting ✓
  - Standard `dHash` implementation (64-bit). ✓
  - Automatic fingerprinting for `image/*` files. ✓
- `dupes` command with similarity threshold ✓
  - `repo dupes --threshold 90` finds similar images using Hamming distance. ✓

## 3) Introduce `sync` command for cross-repo comparison and sync (Partially Completed)

- Command: `diff sync A B [--copyNew] [--deleteMissing] [--mirror]` ✓
  - Implemented in `DiffCommand` and `DiffProcess`. ✓
- Core behavior ✓
  - Compare file inventories and sync based on content. ✓
  - Support for filters (mime, name, size). ✓
- Remaining Sync features:
  - Path mapping rules (currently identical layout only).
  - Dry-run mode for sync operations.
  - Better progress reporting for large syncs.

## 4) Richer comparison strategies (Future)

- Audio fingerprinting (Phase 2)
  - Prototype Chromaprint/AcoustID style fingerprints.
- Extensibility
  - Allow multiple fingerprints per file type.
  - Strategy selection (e.g., `sync --strategy fingerprint`).

## Rollout plan and milestones

1. MessagePack codec and repo metadata. ✓
2. `dupes` command with similarity threshold. ✓
3. `sync` command core functionality. ✓
4. Path mapping and dry-run for `sync`. *
5. Audio fingerprinting prototype.

## Notes

- Maintain backward compatibility (default Jackson for old repos). ✓
- `fingerprintMimetype` was removed in favor of automatic `image/` detection. ✓
- Replaced `Thread.sleep` in tests with Awaitility for better stability. ✓
