package paxel.dedup.data;

public record Repo(String name, String absolutePath, int indices) {

    @Override
    public String toString() {
        return name + ": " + absolutePath;
    }
}
