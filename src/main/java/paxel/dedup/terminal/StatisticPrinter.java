package paxel.dedup.terminal;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StatisticPrinter implements ProgressPrinter {
    private final Map<String, String> values = new ConcurrentHashMap<>();
    private final Map<Integer, String> keys = new ConcurrentHashMap<>();
    private Runnable action;

    @Override
    public int getLines() {
        return values.size();
    }

    public void put(String key, String value) {
        if (!keys.containsValue(key)) {
            keys.put(keys.size(), key);
        }
        values.put(key, value);
        action.run();
    }

    @Override
    public String getLineAt(int row) {
        String key = keys.get(row);
        return "%s: %s".formatted(key, values.get(key));
    }

    @Override
    public void registerChangeListener(Runnable r) {
        this.action = r;
    }
}
