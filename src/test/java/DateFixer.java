import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DateFixer {

    public static void main(String[] args) {

        Path root = Paths.get("/home/axel/pCloudDrive/data/private_photos");

        DateFixer dateFixer = new DateFixer();
        dateFixer.enterPath(root);
    }

    private void enterPath(Path root) {
        try {
            Files.newDirectoryStream(root).forEach(this::fixDirectory);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void fixDirectory(Path f) {
        if (Files.isDirectory(f)) {
            enterPath(fixIfNeeded(f));
        }
        // ignore files so far
    }

    private Path fixIfNeeded(Path f) {
        String s = f.getFileName().toString();
        if (s.length() == 1) {
            try {
                int i = Integer.parseInt(s);
                Path target = f.resolveSibling(String.format("%02d", i));
                try {
                    // return new path
                    Path move = Files.move(f, target);
                    System.out.println("move from " + f + " to " + target);
                    return move;
                } catch (IOException e) {
                    System.err.println("While moving " + f + " to " + target + ": " + e);
                }
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        // return old path
        return f;
    }
}
