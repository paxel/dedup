import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Dater {


    private final static Pattern YYYYMMDD = Pattern.compile("([0-9]{8})");
    private final static Pattern YYYY_MM_DD = Pattern.compile("([0-9]{4}-[0-9]{2}-[0-9]{2})");
    private final static Pattern YYYY_MM_DD_ALL = Pattern.compile("([0-9]{4}_[0-9]{2}_[0-9]{2})");
    private final static Pattern YYYY__MM__DD = Pattern.compile("([0-9]{4})-([0-9]{1})-([0-9]{2})");

    private final List<DatedPath> result = new ArrayList<>();

    private final Path root;

    public Dater(Path root) {
        this.root = root;
    }

    public static void main(String[] args) {

        String[] type = {"jpg", "JPG", "jpeg", "gif", "mov", "png", "dng", "webm", "mp4", "mp", "webp", "3gp", "flv", "avi"};
        for (String s : type) {

           // Path root = Paths.get("/home/axel/documents/private_unsorted_photos_videos/vids");
            Path root = Paths.get("/home/axel/pCloudDrive/ingress/pixel/");
            new Dater(root)
                    .findDates(s)
            //          .printDates();
                    .moveFiles(Paths.get("/home/axel/pCloudDrive/data/private_sorted", s));
        }
    }

    private void moveFiles(Path path) {
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger success = new AtomicInteger();
        AtomicInteger skippedEqual = new AtomicInteger();
        AtomicInteger skippedExisting = new AtomicInteger();
        if (result.isEmpty())
            return;
        System.out.println("Processing " + result.size() + " files: " + path);
        try {
            Files.createDirectories(path);

            result.forEach(f -> {
                Path targetDir = f.getDate().extend(path);
                try {
                    Files.createDirectories(targetDir);
                    Path target = targetDir.resolve(f.getPath().getFileName());
                    if (!target.equals(f.getPath())) {
                        if (!Files.exists(target)) {
                            Files.move(f.path, target);
                            success.incrementAndGet();
                        } else {
                            System.err.println("Failed: " + f.path + " -> " + targetDir + ": target file exists");
                            skippedExisting.incrementAndGet();
                        }
                    } else {
                        skippedEqual.incrementAndGet();
                    }
                } catch (IOException e) {
                    failed.incrementAndGet();
                    System.err.println("Failed: " + f.path + " -> " + targetDir + ": " + e.getMessage());
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        }

        if (failed.get() > 0)
            System.out.println("Failed " + failed.get() + " files");
        if (skippedEqual.get() > 0)
            System.out.println("Skipped " + skippedEqual.get() + " moves to same file");
        if (skippedExisting.get() > 0)
            System.out.println("Skipped " + skippedExisting.get() + " already existing files");
        if (success.get() > 0)
            System.out.println("Moved " + success.get() + " files");

    }

    private void printDates() {
        result.forEach(f -> System.out.println(f.getDate() + ": " + f.path));
    }

    private Dater findDates(String filter) {
        String suffix = "." + filter;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.forEach(f -> {
                if (Files.isRegularFile(f)) {
                    if (f.getFileName().toString().toLowerCase().endsWith(suffix)) {
                        ParsedFileDate parsedFileDate = checkForDate(f, filter);
                        if (parsedFileDate.isValid())
                            result.add(new DatedPath(parsedFileDate, f));
                    }
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    private ParsedFileDate checkForDate(Path f, String filter) {
        String ff = f.getFileName().toString();
        String fileName = ff.substring(0, ff.length() - (filter.length() + 1));
        ParsedFileDate result = parseYyyyMmDd(fileName);
        if (result.isValid())
            return result;
        result = parseYyyy_Mm_Dd(fileName);
        if (result.isValid())
            return result;
        result = parseYyyy_Mm_Dd_all(fileName);
        if (result.isValid())
            return result;
        result = parseYyyy__Mm__Dd(fileName);
        if (result.isValid())
            return result;
        return ParsedFileDate.INVALID;
    }

    private ParsedFileDate parseYyyy__Mm__Dd(String fileName) {
        Matcher matcher = YYYY__MM__DD.matcher(fileName);
        if (matcher.find()) {
            if (matcher.groupCount() >= 3) {
                ParsedFileDate parsedFileDate = ParsedFileDate.parseOther(String.format("%s-%02d-%02d", matcher.group(1), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))));
                if (parsedFileDate.isValid()) {
                    return parsedFileDate;
                }
            }
        }
        return ParsedFileDate.INVALID;
    }

    private ParsedFileDate parseYyyy_Mm_Dd(String fileName) {
        Matcher matcher = YYYY_MM_DD.matcher(fileName);
        if (matcher.find()) {
            for (int i = 0; i < matcher.groupCount(); i++) {
                ParsedFileDate parsedFileDate = ParsedFileDate.parseOther(matcher.group(i));
                if (parsedFileDate.isValid()) {
                    return parsedFileDate;
                }
            }
        }
        return ParsedFileDate.INVALID;
    }

    private ParsedFileDate parseYyyy_Mm_Dd_all(String fileName) {
        Matcher matcher = YYYY_MM_DD_ALL.matcher(fileName);
        if (matcher.find()) {
            for (int i = 0; i < matcher.groupCount(); i++) {
                ParsedFileDate parsedFileDate = ParsedFileDate.parseOther_(matcher.group(i));
                if (parsedFileDate.isValid()) {
                    return parsedFileDate;
                }
            }
        }
        return ParsedFileDate.INVALID;
    }

    private ParsedFileDate parseYyyyMmDd(String fileName) {
        Matcher matcher = YYYYMMDD.matcher(fileName);
        if (matcher.find()) {
            for (int i = 0; i < matcher.groupCount(); i++) {
                ParsedFileDate parsedFileDate = ParsedFileDate.parse(matcher.group(i));
                if (parsedFileDate.isValid()) {
                    return parsedFileDate;
                }
            }
        }
        return ParsedFileDate.INVALID;
    }

    private static final class DatedPath {
        private final ParsedFileDate date;
        private final Path path;

        public DatedPath(ParsedFileDate date, Path path) {
            this.date = date;
            this.path = path;
        }

        public ParsedFileDate getDate() {
            return date;
        }

        public Path getPath() {
            return path;
        }
    }

    private static final class ParsedFileDate {
        private static final int MIN_YEAR = 1970;
        private static final int MAX_YEAR = 2023;
        public static final ParsedFileDate INVALID = new ParsedFileDate(-1, -1, -1);
        private final int year;
        private final int month;
        private final int day;

        public ParsedFileDate(int year, int month, int day) {
            this.year = year;
            this.month = month;
            this.day = day;
        }


        public static ParsedFileDate parse(String isoDateFormattedDate) {
            try {
                LocalDate parse = LocalDate.parse(isoDateFormattedDate, DateTimeFormatter.BASIC_ISO_DATE);
                return new ParsedFileDate(parse.getYear(), parse.getMonthValue(), parse.getDayOfMonth());
            } catch (DateTimeException e) {
                return INVALID;
            }
        }

        public static ParsedFileDate parseOther(String isoLocalDateFormattedDate) {
            try {
                LocalDate parse = LocalDate.parse(isoLocalDateFormattedDate, DateTimeFormatter.ISO_LOCAL_DATE);
                return new ParsedFileDate(parse.getYear(), parse.getMonthValue(), parse.getDayOfMonth());
            } catch (DateTimeException e) {
                return INVALID;
            }
        }

        public static ParsedFileDate parseOther_(String group) {
            try {
                LocalDate parse = LocalDate.parse(group, DateTimeFormatter.ofPattern("yyyy_MM_dd"));
                return new ParsedFileDate(parse.getYear(), parse.getMonthValue(), parse.getDayOfMonth());
            } catch (DateTimeException e) {
                return INVALID;
            }
        }

        public int getYear() {
            return year;
        }

        public int getMonth() {
            return month;
        }

        public int getDay() {
            return day;
        }

        public boolean isValid() {
            if (year <= MIN_YEAR || year > MAX_YEAR) {
                return false;
            }
            if (month < 1 || month > 12) {
                return false;
            }
            // weak check
            return day >= 1 && day <= 31;
        }

        @Override
        public String toString() {
            return String.format("%04d.%02d.%02d", year, month, day);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ParsedFileDate that)) return false;
            return year == that.year && month == that.month && day == that.day;
        }

        @Override
        public int hashCode() {
            return Objects.hash(year, month, day);
        }

        public Path extend(Path base){
            return base.resolve(""+year).resolve(String.format("%02d", month)).resolve(String.format("%02d", day));
        }
    }
}
