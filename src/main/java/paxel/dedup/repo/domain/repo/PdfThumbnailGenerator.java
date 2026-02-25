package paxel.dedup.repo.domain.repo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import paxel.dedup.domain.port.out.FileSystem;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Base64;

@Slf4j
@RequiredArgsConstructor
public class PdfThumbnailGenerator {
    private final FileSystem fileSystem;

    public String generateBase64Thumbnail(Path path) {
        try (InputStream is = fileSystem.newInputStream(path);
             PDDocument document = PDDocument.load(is)) {
            if (document.getNumberOfPages() > 0) {
                PDFRenderer pdfRenderer = new PDFRenderer(document);
                BufferedImage bim = pdfRenderer.renderImageWithDPI(0, 72); // 72 DPI is enough for thumbnail
                return toBase64(bim);
            }
        } catch (Exception e) {
            log.warn("Failed to generate PDF thumbnail for {}: {}", path, e.getMessage());
        }
        return null;
    }

    private String toBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}
