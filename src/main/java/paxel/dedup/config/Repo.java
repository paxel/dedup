package paxel.dedup.config;

import java.nio.file.Path;

public record Repo(String name, Path path, int indices) {
}
