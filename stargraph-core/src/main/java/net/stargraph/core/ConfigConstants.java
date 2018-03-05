package net.stargraph.core;

public final class ConfigConstants {
    private ConfigConstants() {
    }

    // elastic config
    public static String elasticScrollTimeKey = "stargraph.elastic.scroll.time";
    public static String elasticScrollSizeKey = "stargraph.elastic.scroll.size";


    public static final String idxStorePath = "index-store.factory.class";
}
