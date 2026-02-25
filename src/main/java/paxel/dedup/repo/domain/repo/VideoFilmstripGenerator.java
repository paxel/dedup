package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import paxel.dedup.domain.port.out.FileSystem;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class VideoFilmstripGenerator {
    private final FileSystem fileSystem;

    public List<String> generateBase64Filmstrip(Path path) {
        List<String> filmstrip = new ArrayList<>();
        try {
            // JCodec needs a SeekableByteChannel. We can use NIO or a temporary file.
            // Since our FileSystem port doesn't have a SeekableByteChannel yet,
            // we'll use the real file path for JCodec as it's a library constraint.
            // In a strict hexagonal setup, we'd need a SeekableByteChannel port.

            FrameGrab grab = FrameGrab.createFrameGrab(org.jcodec.common.io.NIOUtils.readableChannel(path.toFile()));
            double duration = grab.getVideoTrack().getMeta().getTotalDuration();

            double[] percentages = {0.1, 0.5, 0.9};
            for (double p : percentages) {
                grab.seekToSecondPrecise(duration * p);
                Picture picture = grab.getNativeFrame();
                if (picture != null) {
                    BufferedImage bufferedImage = AWTUtil.toBufferedImage(picture);
                    filmstrip.add(toBase64(bufferedImage));
                }
            }
        } catch (Exception e) {
            log.info("Failed to generate video filmstrip for {}: {} (Using generic icon)", path, e.getMessage());
        }
        return filmstrip;
    }

    private String toBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}
