# Saturday Plan

This document outlines the Saturday work plan to evolve Dedup in four areas: native packaging with GraalVM, pluggable line codecs with MessagePack support, a new `dupes` similarity command, and richer comparison strategies beyond plain hashes.

## 1) Switch to GraalVM and compile Dedup to a native executable (Completed)

- Assess current runtime features vs. native-image constraints ✓
- Build setup ✓
  - Adopt GraalVM JDK. ✓
  - Add `org.graalvm.buildtools:native-maven-plugin` to `pom.xml`. ✓
- Code adjustments ✓
  - Jackson-friendly repo persistence without reflection. ✓
- CI and deliverables ✓
  - Maven profile `-Pnative` producing `dedup` binary. ✓
- Validation ✓
  - Verified basic CLI functionality in native mode. ✓

## 2) Add LineCodec: MessagePack, with repo-level codec selection (Completed)

- Abstractions ✓
  - Introduced `LineCodec` SPI and `MessagePackRepoFileCodec`. ✓
- Repo-level codec selection ✓
  - `repo create --codec messagepack` (now default). ✓
  - Persist codec in `dedup_repo.yml`. ✓
- Migration ✓
  - `repo prune --change-codec <codec>` allows migrating existing repos. ✓

## 3) Similarity detection and advanced filtering (Completed)

- Advanced size filtering ✓
  - Support for `>`, `<`, `>=`, `<=`, and `=`. ✓
- Image fingerprinting ✓
  - Standard `dHash` implementation (64-bit). ✓
  - Automatic fingerprinting for `image/*` files. ✓
- `dupes` command with similarity threshold ✓
  - `repo dupes --threshold 90` finds similar images using Hamming distance. ✓

## 4) Introduce `sync` command for cross-repo comparison and sync (Partially Completed)

- Command: `diff sync A B [--copyNew] [--deleteMissing] [--mirror]` ✓
  - Implemented in `DiffCommand` and `DiffProcess`. ✓
- Core behavior ✓
  - Compare file inventories and sync based on content. ✓
  - Support for filters (mime, name, size). ✓
- Remaining Sync features:
  - Path mapping rules (currently identical layout only).
  - Dry-run mode for sync operations.
  - Better progress reporting for large syncs.

## 5) Richer comparison strategies (Future)

- Audio fingerprinting (Phase 2)
  - Prototype Chromaprint/AcoustID style fingerprints.
- Extensibility
  - Allow multiple fingerprints per file type.
  - Strategy selection (e.g., `sync --strategy fingerprint`).

## Rollout plan and milestones

1. GraalVM native build green. ✓
2. MessagePack codec and repo metadata. ✓
3. `dupes` command with similarity threshold. ✓
4. `sync` command core functionality. ✓
5. Path mapping and dry-run for `sync`. *
6. Audio fingerprinting prototype.

## Notes

- Maintain backward compatibility (default Jackson for old repos). ✓
- Keep dependencies lean for GraalVM compatibility. ✓
- `fingerprintMimetype` was removed in favor of automatic `image/` detection. ✓
- Replaced `Thread.sleep` in tests with Awaitility for better stability. ✓
