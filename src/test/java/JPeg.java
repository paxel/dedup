import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class JPeg {

    private final Path root;
    private List<Path> deletez = new ArrayList<>();


    public JPeg(Path path) {
        this.root = path;
    }

    public static void main(String[] args) {
        new JPeg(Paths.get("/home/axel/documents/private_photos"))
                .findDups()
                .printDups();
    }

    private JPeg findDups() {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.forEach(f -> {
                if (Files.isRegularFile(f)) {
                    if (f.getFileName().toString().endsWith(".jpg")) {
                        verifyAndPrint(f);
                    }
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }


    private void printDups(){
        System.out.println("Found "+deletez.size()+" candidates");
    }

    private void verifyAndPrint(Path f) {
        String src = f.getFileName().toString();
        String dst = src.substring(0, src.length() - 4) + ".dmg";
        Path sibling = f.resolveSibling(dst);
        if (true) {
            if (Files.exists(sibling) && Files.isRegularFile(sibling)) {
                try {
                    long size = Files.size(f);
                    long size1 = Files.size(sibling);
                    if (size > size1) {
                        deletez.add(sibling);
                    } else if (size == size1) {
                        System.out.println("jpg=JPG: " + sibling);
                    } else {
                        deletez.add(f);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
