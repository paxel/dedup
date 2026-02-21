package paxel.dedup.domain.model;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

public class ImageFingerprinter {

    /**
     * Calculates a simple dHash (difference hash) for an image.
     * This is a standard implementation and does not require external NIH libraries.
     */
    public String calculateFingerprint(Path path) {
        try {
            BufferedImage img = ImageIO.read(path.toFile());
            if (img == null) return null;

            // Standard dHash: resize to 9x8, grayscale, compare adjacent pixels
            BufferedImage scaled = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, 9, 8, null);
            g.dispose();

            long hash = 0;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int left = scaled.getRaster().getSample(x, y, 0);
                    int right = scaled.getRaster().getSample(x + 1, y, 0);
                    if (left > right) {
                        hash |= (1L << (y * 8 + x));
                    }
                }
            }
            return String.format("%016x", hash);
        } catch (IOException | RuntimeException e) {
            // Silently fail for fingerprinting, it's optional
            return null;
        }
    }
}
