# Saturday Plan

This document outlines the Saturday work plan to evolve Dedup in four areas: native packaging with GraalVM, pluggable line codecs with MessagePack support, a new `dupes` sync command, and richer comparison strategies beyond plain hashes.

## 1) Switch to GraalVM and compile Dedup to a native executable

- Assess current runtime features vs. native-image constraints
  - Reflection, dynamic proxies, ServiceLoader usage, resources on classpath, and Jackson databind usage.
  - Generate reflection/resource configs where needed (via `native-image-agent` in a warm run or manual configs).
- Build setup
  - Adopt GraalVM JDK (via SDKMAN or Maven toolchain). Pin a version LTS.
  - Add `org.graalvm.buildtools:native-maven-plugin` to `pom.xml` with profiles: `native` and default JVM.
  - Ensure `--no-fallback`, `-H:+ReportExceptionStackTraces`, and resource/reflection configs are wired.
- Code adjustments (only if required)
  - Replace unsupported dynamic features or register them explicitly (e.g., Jackson modules, parameter names).
  - Verify logging backends and charset usage.
- CI and deliverables
  - Add a Maven profile `-Pnative` producing a single self-contained binary `dedup`.
  - Publish artifacts for Linux first; document Mac/Windows follow-up.
- Validation
  - Run core commands on small repos to compare behavior/perf vs JVM.

## 2) Add LineCodec: MessagePack, with repo-level codec selection and fallback to Jackson

- Abstractions
  - Confirm/introduce `LineCodec` SPI abstraction if not already present.
  - Existing `JacksonLineCodec` remains default.
- New implementation
  - Create `MessagePackLineCodec` using `org.msgpack:msgpack-core` (zero-dependency core) or msgpack-jackson if leveraging existing POJOs.
  - Provide round-trip tests for typical domain objects/lines.
- Repo-level codec selection
  - When creating a repo, allow a `--codec` option: `jackson` (default) or `msgpack`.
  - Persist chosen codec in the repo metadata file (e.g., `.dedup/repo.json` field `lineCodec = "jackson"|"msgpack"`).
  - On repo open, read codec from metadata and instantiate the matching codec; fallback policy:
    - If metadata is missing/unknown → use `JacksonLineCodec` (backward compatible).
    - If specified codec is unavailable at runtime → warn and fallback to `JacksonLineCodec` unless `--strict` is set.
- Migration
  - Provide a one-off `convert-codec` command to rewrite lines between codecs (optional, stretch goal).

## 3) Introduce `dupes` command for cross-repo comparison and sync

- Command: `dedup dupes A B [--copyNew] [--deleteMissing] [--mirror] [--dryRun]`
  - `A` is source repo, `B` is target repo.
  - `--copyNew`: copy files that exist in A but not in B.
  - `--deleteMissing`: delete files in B that no longer exist in A.
  - `--mirror`: equivalent to `--copyNew --deleteMissing`.
  - `--dryRun`: report the plan without making changes.
- Core behavior
  - Compare file inventories via chosen comparison strategy (see section 4).
  - Resolve path mapping rules (identical layout initially; configurable mapping as follow-up).
  - Provide progress and a concise summary: counts of equal, new, deleted, copied, removed, skipped, errors.
- Safety
  - Default to `--dryRun`; require explicit confirmation flags for destructive ops or accept `--yes`.
  - Ensure safe filesystem operations (temp writes, atomic moves where possible).
- Tests
  - Unit tests for diffing logic and option parsing.
  - Integration test using two temp repos with small fixture sets.

## 4) Richer comparison strategies (beyond hashes)

- Strategy SPI
  - Define `CompareStrategy` interface with at least: `HASH`, `IMAGE_FINGERPRINT`, `AUDIO_FINGERPRINT`.
  - Make strategy selectable via CLI and repo config; default remains `HASH` (current behavior).
- Image fingerprinting (phase 1)
  - Implement perceptual hash (pHash/aHash/dHash) using a small imaging lib (e.g., TwelveMonkeys/ImageIO + simple DCT).
  - Store fingerprints in repo index (extend schema with optional fields), compute lazily on demand or during update.
- Audio fingerprinting (phase 2)
  - Prototype Chromaprint/AcoustID style fingerprints via a lightweight Java lib, or shell out to `fpcalc` if available, with caching.
- Extensibility
  - Allow multiple fingerprints per file type; priority order configurable.
  - Cache invalidation when file size/timestamp changes.
- Reporting
  - CLI flags to choose tolerance/threshold for perceptual similarity (e.g., Hamming distance for pHash).

## Rollout plan and milestones

1. GraalVM native build green on CI for core commands (read-only ops).
2. MessagePack codec implemented; repo metadata persisted; backward-compatible open/read verified.
3. `dupes` command functional with `HASH` strategy and `--dryRun` default.
4. Safe sync operations (`--copyNew`, `--deleteMissing`, `--mirror`) fully tested on temp repos.
5. Image fingerprinting (pHash) available and integrated into comparison strategies.
6. Optional: audio fingerprinting prototype behind a feature flag.

## Notes

- Maintain backward compatibility for existing repos (default Jackson; metadata optional).
- Keep dependencies lean to preserve GraalVM native-image size and compatibility.
- Document all new CLI options and repo metadata fields in `README.md` and man pages.
