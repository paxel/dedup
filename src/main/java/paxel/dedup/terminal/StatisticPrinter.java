package paxel.dedup.terminal;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class StatisticPrinter implements ProgressPrinter {
    private final List<Supplier<String>> lines = new ArrayList<>();
    private Runnable action;
    private String repo = "";
    private String path = "";
    private String progress = "";
    private String files = "";
    private String deleted = "";
    private String hashed = "";
    private String unchanged = "";
    private String duration = "";
    private String directories = "";
    private String errors = "none";
    private final Map<String, Long> mimetypes = new HashMap<>();

    public StatisticPrinter() {
        lines.add(() -> "       Repo: " + repo + "  -  Path: " + path);
        lines.add(() -> "   Progress: " + progress);
        lines.add(() -> "   Duration: " + duration);
        lines.add(() -> "Directories: " + directories);
        lines.add(() -> "      Files: " + files);
        lines.add(() -> "    Deleted: " + deleted);
        lines.add(() -> "     Hashed: " + hashed);
        lines.add(() -> "  Unchanged: " + unchanged);
        lines.add(() -> "     Errors: " + errors);
        lines.add(() -> " Mime-Types: " + mimetypes.entrySet().stream().sorted(new Comparator<Map.Entry<String, Long>>() {
            @Override
            public int compare(Map.Entry<String, Long> o1, Map.Entry<String, Long> o2) {
                return Long.compare(o1.getValue(), o2.getValue()) * -1;
            }
        }).map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(", ")));
    }

    @Override
    public int getLines() {
        return lines.size();
    }

    public void set(String repo, String path) {
        this.repo = repo;
        this.path = path;
        action.run();
    }


    @Override
    public String getLineAt(int row) {
        if (row < lines.size())
            return lines.get(row).get();
        return "._.";
    }

    @Override
    public void registerChangeListener(Runnable r) {
        this.action = r;
    }

    public void setProgress(String progress) {
        this.progress = progress;
        action.run();
    }

    public void setFiles(String files) {
        this.files = files;
        action.run();
    }

    public void setDeleted(String deleted) {
        this.deleted = deleted;
        action.run();
    }

    public void addMimeType(String mimetype, long count) {
        mimetypes.put(mimetype, count);
        action.run();
    }

    public void setHashed(String hashed) {
        this.hashed = hashed;
        action.run();
    }

    public void setUnchanged(String unchanged) {
        this.unchanged = unchanged;
        action.run();
    }

    public void setDuration(String duration) {
        this.duration = duration;
        action.run();
    }

    public void setDirectories(String directories) {
        this.directories = directories;
        action.run();
    }

    public void setErrors(String errors) {
        this.errors = errors;
        action.run();
    }
}
