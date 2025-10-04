package paxel.dedup.config;

public class DedupConfigFactory {
    public static DedupConfig create(){
        return new DefaultDedupConfig();
    }
}
