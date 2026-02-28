package paxel.dedup.terminal;

import lombok.Setter;
import paxel.dedup.domain.model.ProgressUpdate;
import paxel.dedup.domain.service.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class StatisticPrinter implements ProgressPrinter {
    private final List<Supplier<String>> lines = new ArrayList<>();
    private Runnable action;
    @Setter
    private EventBus eventBus;

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
        lines.add(() -> " Mime-Types: " + mimetypes.entrySet().stream().sorted((o1, o2) -> Long.compare(o1.getValue(), o2.getValue()) * -1).map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(", ")));
    }

    private void notifyListeners() {
        if (action != null) {
            action.run();
        }
    }

    @Override
    public void update(ProgressUpdate update) {
        if (update.getRepo() != null) this.repo = update.getRepo();
        if (update.getPath() != null) this.path = update.getPath();
        if (update.getStatus() != null) this.progress = update.getStatus();
        if (update.getEta() != null) {
            this.progress += " ETA: " + update.getEta();
        }
        if (update.getDuration() != null) this.duration = update.getDuration();
        if (update.getErrors() != null) this.errors = update.getErrors();
        if (update.getProgressPercent() != null) {
            this.progress = "%.2f %% %s".formatted(update.getProgressPercent(), this.progress);
        }

        if (update.getDirectoriesProcessed() != null && update.getDirectoriesTotal() != null) {
            this.directories = update.getDirectoriesProcessed() + " / " + update.getDirectoriesTotal();
        }
        if (update.getFilesProcessed() != null && update.getFilesTotal() != null) {
            this.files = update.getFilesProcessed() + " / " + update.getFilesTotal();
        }
        if (update.getHashedProcessed() != null && update.getHashedTotal() != null) {
            this.hashed = update.getHashedProcessed() + " / " + update.getHashedTotal();
        }
        if (update.getUnchangedProcessed() != null && update.getUnchangedTotal() != null) {
            this.unchanged = update.getUnchangedProcessed() + " / " + update.getUnchangedTotal();
        }
        if (update.getDeletedProcessed() != null && update.getDeletedTotal() != null) {
            this.deleted = update.getDeletedProcessed() + " / " + update.getDeletedTotal();
        }
        if (update.getMimeDistribution() != null) {
            this.mimetypes.putAll(update.getMimeDistribution());
        }

        if (action != null) {
            action.run();
        }
        if (eventBus != null) {
            eventBus.publish("progress", update);
        }
    }

    @Override
    public int getLines() {
        return lines.size();
    }

    public void set(String repo, String path) {
        this.repo = repo;
        this.path = path;
        notifyListeners();
        if (eventBus != null) {
            eventBus.publish("progress", ProgressUpdate.builder().repo(repo).path(path).build());
        }
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
        notifyListeners();
    }

    public void setFiles(String files) {
        this.files = files;
        notifyListeners();
    }

    public void setDeleted(String deleted) {
        this.deleted = deleted;
        notifyListeners();
    }

    public void addMimeType(String mimetype, long count) {
        mimetypes.put(mimetype, count);
        notifyListeners();
    }

    public void setHashed(String hashed) {
        this.hashed = hashed;
        notifyListeners();
    }

    public void setUnchanged(String unchanged) {
        this.unchanged = unchanged;
        notifyListeners();
    }

    public void setDuration(String duration) {
        this.duration = duration;
        notifyListeners();
    }

    public void setDirectories(String directories) {
        this.directories = directories;
        notifyListeners();
    }

    public void setErrors(String errors) {
        this.errors = errors;
        notifyListeners();
    }

    @Override
    public void finish() {
        this.progress = "100.00 % Finished";
        notifyListeners();
        if (eventBus != null) {
            eventBus.publish("finished", Map.of("repo", repo));
        }
    }
}
