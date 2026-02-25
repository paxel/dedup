package paxel.dedup.domain.model;

import lombok.extern.slf4j.Slf4j;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

@Slf4j
public class VideoFingerprinter {
    private final ImageFingerprinter imageFingerprinter = new ImageFingerprinter();

    public String calculateTemporalHash(Path path) {
        try {
            // JCodec FrameGrab can be flaky with some AVI or older codecs.
            // We use the highest level API first.
            FrameGrab grab = FrameGrab.createFrameGrab(org.jcodec.common.io.NIOUtils.readableChannel(path.toFile()));
            double duration = grab.getVideoTrack().getMeta().getTotalDuration();
            if (duration <= 0) return null;

            StringBuilder sb = new StringBuilder();
            double[] percentages = {0.1, 0.5, 0.9};
            for (double p : percentages) {
                grab.seekToSecondPrecise(duration * p);
                Picture picture = grab.getNativeFrame();
                if (picture != null) {
                    BufferedImage bufferedImage = AWTUtil.toBufferedImage(picture);
                    ImageFingerprinter.FingerprintResult fr = imageFingerprinter.calculate(bufferedImage);
                    if (fr.fingerprint() != null) {
                        sb.append(fr.fingerprint());
                    } else {
                        sb.append("0000000000000000");
                    }
                } else {
                    sb.append("0000000000000000");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            // If JCodec fails to decode, we try a content-based fallback hash to still support duplicate detection.
            log.info("Could not calculate temporal hash for {} using JCodec ({}). Falling back to content-based hash.", path, e.getMessage());
            return calculateFallbackHash(path);
        }
    }

    private String calculateFallbackHash(Path path) {
        try (java.io.InputStream is = java.nio.file.Files.newInputStream(path)) {
            // Skip potential 1MB header to avoid container/metadata variation
            is.skip(1024 * 1024);
            byte[] chunk = new byte[100 * 1024]; // 100KB chunk
            int n = is.read(chunk);
            if (n <= 0) return null;

            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(chunk, 0, n);
            return "fallback:" + java.util.HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            log.debug("Fallback hash also failed for {}: {}", path, e.getMessage());
            return null;
        }
    }
}
