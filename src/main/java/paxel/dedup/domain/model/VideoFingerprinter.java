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
            log.warn("Failed to calculate temporal hash for {}: {}", path, e.getMessage());
            return null;
        }
    }
}
