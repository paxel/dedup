package paxel.dedup.domain.model;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

public class ImageFingerprinter {

    /**
     * Calculates a simple dHash (difference hash) for an image.
     * The image is normalized (rotation and mirroring) to be invariant to these transformations.
     */
    public String calculateFingerprint(Path path) {
        try {
            BufferedImage img = ImageIO.read(path.toFile());
            if (img == null) return null;

            // 1. Resize to 9x9 grayscale for normalization and dHash
            BufferedImage scaled = new BufferedImage(9, 9, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, 9, 9, null);
            g.dispose();

            // 2. Extract pixels to a simple byte array to avoid multiple BufferedImage allocations
            byte[] pixels = new byte[81];
            scaled.getRaster().getDataElements(0, 0, 9, 9, pixels);

            // 3. Canonicalize orientation (all 8 Dihedral variants)
            pixels = canonicalize(pixels);

            // 4. Compute 64-bit dHash (8x8 differences)
            long hash = 0;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int left = pixels[y * 9 + x] & 0xFF;
                    int right = pixels[y * 9 + (x + 1)] & 0xFF;
                    if (left > right) {
                        hash |= (1L << (y * 8 + x));
                    }
                }
            }
            return String.format("%016x", hash);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private byte[] canonicalize(byte[] img) {
        byte[] best = null;

        byte[] current = img;
        for (int r = 0; r < 4; r++) {
            // Check current rotation
            if (best == null || compare(current, best) > 0) {
                best = current.clone();
            }

            // Check diagonal flip (transpose) of current rotation
            byte[] flipped = flipDiagonal(current);
            if (compare(flipped, best) > 0) {
                best = flipped; // flipDiagonal already returns a new array
            }

            if (r < 3) { // No need to rotate for the last iteration
                current = rotate90(current);
            }
        }
        return best;
    }

    private int compare(byte[] a, byte[] b) {
        for (int i = 0; i < 81; i++) {
            int v1 = a[i] & 0xFF;
            int v2 = b[i] & 0xFF;
            if (v1 != v2) return v1 - v2;
        }
        return 0;
    }

    private byte[] rotate90(byte[] img) {
        byte[] rotated = new byte[81];
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                // (x, y) -> (8-y, x)
                rotated[x * 9 + (8 - y)] = img[y * 9 + x];
            }
        }
        return rotated;
    }

    private byte[] flipDiagonal(byte[] img) {
        byte[] flipped = new byte[81];
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                // (x, y) -> (y, x)
                flipped[x * 9 + y] = img[y * 9 + x];
            }
        }
        return flipped;
    }
}
