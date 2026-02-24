package paxel.dedup.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ImageFingerprinterTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldBeInvariantToRotationAndMirroring() throws IOException {
        // Create a simple asymmetric image (e.g., a "L" shape)
        BufferedImage baseImg = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = baseImg.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 100, 100);
        g.setColor(Color.WHITE);
        g.fillRect(10, 10, 20, 80); // vertical part
        g.fillRect(10, 70, 60, 20); // horizontal part
        g.dispose();

        Path baseFile = tempDir.resolve("base.png");
        ImageIO.write(baseImg, "png", baseFile.toFile());

        ImageFingerprinter fingerprinter = new ImageFingerprinter();
        String baseFingerprint = fingerprinter.calculate(baseFile).fingerprint();
        assertThat(baseFingerprint).isNotNull();

        // Test rotations
        for (int angle : new int[]{90, 180, 270}) {
            BufferedImage rotatedImg = rotate(baseImg, angle);
            Path rotatedFile = tempDir.resolve("rotated_" + angle + ".png");
            ImageIO.write(rotatedImg, "png", rotatedFile.toFile());
            String rotatedFingerprint = fingerprinter.calculate(rotatedFile).fingerprint();
            assertThat(rotatedFingerprint).isEqualTo(baseFingerprint)
                    .as("Fingerprint for " + angle + " rotation should match");
        }

        // Test horizontal flip
        BufferedImage flippedHImg = flipHorizontal(baseImg);
        Path flippedHFile = tempDir.resolve("flippedH.png");
        ImageIO.write(flippedHImg, "png", flippedHFile.toFile());
        String flippedHFingerprint = fingerprinter.calculate(flippedHFile).fingerprint();
        assertThat(flippedHFingerprint).isEqualTo(baseFingerprint)
                .as("Fingerprint for horizontal flip should match");

        // Test vertical flip
        BufferedImage flippedVImg = flipVertical(baseImg);
        Path flippedVFile = tempDir.resolve("flippedV.png");
        ImageIO.write(flippedVImg, "png", flippedVFile.toFile());
        String flippedVFingerprint = fingerprinter.calculate(flippedVFile).fingerprint();
        assertThat(flippedVFingerprint).isEqualTo(baseFingerprint)
                .as("Fingerprint for vertical flip should match");
    }

    private BufferedImage rotate(BufferedImage img, int angle) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage rotated = new BufferedImage(w, h, img.getType());
        Graphics2D g = rotated.createGraphics();
        g.rotate(Math.toRadians(angle), w / 2.0, h / 2.0);
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return rotated;
    }

    private BufferedImage flipHorizontal(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage flipped = new BufferedImage(w, h, img.getType());
        Graphics2D g = flipped.createGraphics();
        g.drawImage(img, w, 0, -w, h, null);
        g.dispose();
        return flipped;
    }

    private BufferedImage flipVertical(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage flipped = new BufferedImage(w, h, img.getType());
        Graphics2D g = flipped.createGraphics();
        g.drawImage(img, 0, h, w, -h, null);
        g.dispose();
        return flipped;
    }
}
