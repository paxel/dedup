import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class Memer {

    private final String source;
    private final String dest;

    public Memer(String source, String dest) {

        this.source = source;
        this.dest = dest;
    }

    public static void main(String[] args) {
        new Memer("/home/axel/pCloudDrive/ingress/pixel/", "/home/axel/pCloudDrive/data/public_images/memes/").sort();

    }

    private void sort() {
        try {
            Path target = Path.of(dest);
            Files.createDirectories(target);
            try (Stream<Path> walk = Files.walk(Path.of(source))) {
                walk.forEach(f -> {
                    if (Files.isRegularFile(f)) {
                        dememe(f, target);
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void dememe(Path f, Path target) {
        Path fileName = f.getFileName();
        if (fileName.toString().matches("[0-9a-zA-Z]{7}\\.png")
                || fileName.toString().matches("[0-9a-zA-Z]{7}\\.jpg")
                || fileName.toString().matches("[0-9a-zA-Z]{7}\\.mp4")) {
            try {
                Path move = Files.move(f, target.resolve(fileName));
            } catch (FileAlreadyExistsException e) {
                System.out.println("Skipping existing file " + fileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
