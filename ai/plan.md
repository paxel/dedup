# Multi-Type Similarity Plan: Detailed Analysis & Roadmap

This document provides a detailed technical strategy for extending similarity detection to Video, Audio, and PDF files using "low-hanging fruit" techniques that minimize complexity and external dependencies.

## 1. Analysis of "Low-Hanging Fruit" Tools

| File Type | Library (Current/Proposed) | Strategy | Complexity |
| :--- | :--- | :--- | :--- |
| **All** | **Apache Tika** (Current) | Metadata extraction (Duration, Artist, Title, PageCount). | Low |
| **Video** | **JCodec** (Proposed) | Pure-Java frame extraction. No `ffmpeg` native binary required. | Medium |
| **Audio** | **Apache Tika** (Current) | Duration + Sample Rate + Bitrate comparison. | Low |
| **PDF** | **PDFBox** (via Tika) | Text content hashing & First-page thumbnail. | Medium |

---

## 2. Technical Comparison Strategies

### 2.1 Video Similarity (Keyframe Hashing)
Video files are often re-encoded or have different containers (MKV vs MP4). 
*   **Hash Algorithm:** 
    1. Extract exactly 3 frames (at 10%, 50%, 90% of duration).
    2. Convert to 9x9 grayscale (reuse `ImageFingerprinter` logic).
    3. Concatenate the 3 hashes into a single 192-bit "Temporal Hash" stored as a Hex string.
*   **Tie-breaker:** Metadata duration (must be within +/- 2 seconds).
*   **Storage:** Added to `RepoFile` as `videoHash`.

### 2.2 Audio Similarity (Acoustic Anchoring)
Real acoustic fingerprinting (Chromaprint) is heavy. 
*   **Hash Algorithm:** 
    1. Extract `x-tika:duration`, `samplerate`, and `channels`.
    2. Combine with a "Content Chunk Hash": Hash the first 100KB of the actual audio stream (skipping ID3 tags/headers).
    3. Similarity is defined as: `DurationMatch && ChunkHashMatch`.
*   **Storage:** Added to `RepoFile` as `audioHash`.

### 2.3 PDF Similarity (Structural & Textual)
*   **Hash Algorithm:** 
    1. Extract `Page-Count` and `Author`.
    2. Extract plain text content using Tika.
    3. Store a hash of the first 5000 characters of text (normalized: lowercase, no whitespace).
*   **Similarity:** Matching text hash + matching page count.

---

## 3. Least Effort Roadmap (Prioritized)

To provide immediate value with minimal code changes, we follow this sequence:

### Phase 1: Metadata Enrichment (Total Effort: 4 hours)
*   **Goal:** Capture stable metadata during `repo update`.
*   **Action:** Update `RepoFile` to include a `Map<String, String> attributes`.
*   **Indexing:** `RepoManager` uses `Tika` to populate attributes like `duration`, `width`, `height`, `pages`, `artist`.
*   **Result:** `repo dupes` can now group by `Duration` (for media) or `PageCount` (for PDF) as a "soft" similarity check.

### Phase 2: Video/PDF Thumbnails (Total Effort: 6 hours)
*   **Goal:** Visual verification in `--interactive` mode.
*   **Action:** 
    *   Integrate `JCodec` for video frame extraction.
    *   Use `Tika/PDFBox` for PDF first-page rendering.
*   **Result:** Users see what's inside a video/PDF without opening external apps.

### Phase 3: Advanced Similarity Hashing (Total Effort: 8 hours)
*   **Goal:** Automated similarity grouping.
*   **Action:** Implement the "Keyframe Hashing" and "Text Hashing" logic described in Section 2.
*   **Result:** `repo dupes --threshold` works for Videos and PDFs.

---

## 4. Interactive Mode Integration

### 4.1 Video Filmstrip
Instead of a single image, show a 3-frame "filmstrip" (10%/50%/90%) in the `file-card`. This immediately reveals if two videos are the same content even if they have different watermarks or black bars.

### 4.2 Metadata Comparison Table
Display a "Side-by-Side" metadata comparison:
| Feature | File A | File B | Match? |
| :--- | :--- | :--- | :--- |
| Duration | 01:20:05 | 01:20:04 | ✅ |
| Bitrate | 5000 kbps | 2400 kbps | ❌ |
| Codec | h264 | vp9 | ❌ |

---

## 5. Next Steps (Actionable Items)

1.  **Dependency Update:** Add `jcodec` and `jcodec-javase` to `pom.xml`.
2.  **Model Extension:** Add `Map<String, String> attributes` to `RepoFile`.
3.  **Indexing:** Create `MetadataExtractor` service that wraps Tika to populate these attributes.
4.  **UI:** Update `InteractiveDupeProcess` to display a "Metadata" section in the card.
