package net.stargraph.core;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import net.stargraph.StargraphConfigurationException;
import net.stargraph.core.search.database.DBType;
import net.stargraph.model.IndexID;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ConfigHandler {
    //defaults
    public static final String defaultIndicesFactoryClass = "indices.factory.class";
    public static final String indicesFallbackSearcher = "indices.fallback.searcher";
    public static final String defaultIndexSearcher = "indices.%s.searcher";


    //index
    public static final String indexSearcher = "searcher.class";
    public static final String elasticBulkConcurrency = "elastic.bulk.concurrency";
    public static final String elasticBulkActions = "elastic.bulk.actions";
    public static final String indexFactoryClass = "index-store.factory.class";
    public static final String dataProviderClass = "provider.class";


    //kb
    public static final String dbImplPath = "db.class";
    public static final String dbTypePath = "db.type";
    public static final String language = "language";
    public static final String index = "model";
    public static final String hdtUseIndex = "triple-store.hdt.use-index";
    public static final String hdtFile = "triple-store.hdt.file";
    private static final String composedOf = "composed";

    // elastic config
    public static String elasticScrollTimeKey = "stargraph.elastic.scroll.time";
    public static String elasticScrollSizeKey = "stargraph.elastic.scroll.size";

    //main
    private static final String distServiceCorpus = "distributional-service.corpus";
    public static final String distServiceURL = "distributional-service.rest-url";
    public static final String kb = "kb";
    public static final String dataRootDir = "data.root-dir";
    public static final String robust = "robust";
    public static final String defaults = "default";


    private Config mainConfig;

    public static String processors(IndexID indexID) {
        return String.format("%s.processors", indexID.getIndexPath());
    }

    public static String kbPath(String kbName) {
        return kb + "." + kbName;
    }


    public static String defaultDBClass(String clsName) {
        return String.format("db.%s.class", clsName);
    }

    ConfigHandler(Config mainConfig) {
        this.mainConfig = Objects.requireNonNull(mainConfig);
    }

    public Config getMainConfig() {
        return mainConfig;
    }

    public Boolean isRobust() {
        return mainConfig.getBoolean(robust);
    }

    public Config getKBConfig(String kbName) {
        Objects.requireNonNull(kbName);
        if (!mainConfig.hasPath(kbPath(kbName))) {
            throw new IllegalArgumentException("Trying to retrieve config of '" + kbName
                    + "' but there is no entry with that name!");
        }
        return mainConfig.getConfig(kbPath(kbName));
    }

    public Config getDefaultsConfig() {
        return mainConfig.getConfig(defaults);
    }

    public Config getIndexConfig(IndexID indexID) {
        return mainConfig.getConfig(indexID.getIndexPath());
    }

    public List<? extends Config> getProcessorsCfg(IndexID indexID) {
        String path = processors(indexID);
        if (mainConfig.hasPath(path)) {
            return mainConfig.getConfigList(path);
        }
        return null;
    }


    public String dataRootDir() {
        return mainConfig.getString(dataRootDir);
    }

    public String distServiceUrl() {
        return mainConfig.getString(distServiceURL);

    }

    public String distServiceCorpus() {
        return mainConfig.getString(distServiceCorpus);
    }

    /**
     * This checks whether the KB is enabled.
     * <p>
     *
     * @param kbName Name of kb to check.
     * @return <code>true</code> if enabled, <code>false</code> otherwise.
     */
    public boolean isEnabled(String kbName) {
        return getKBConfig(kbName).getBoolean("enabled");
    }

    public Set<String> knowledgeBases() {
        if (mainConfig.hasPathOrNull(kb)) {
            if (mainConfig.getIsNull(kb)) {
                throw new StargraphConfigurationException("No KBs configured in the config file!");
            }
        }
        Set<String> kbs = mainConfig.getObject(kb).keySet();
        if (kbs.isEmpty()) {
            throw new StargraphConfigurationException("No KBs configured in the config file!");
        }
        return kbs;
    }

    public String dataProviderClassName(IndexID indexID) {
        return getIndexConfig(indexID).getString(dataProviderClass);
    }

    public String indexFactoryClass(IndexID indexID) {
        return getIndexConfig(indexID).hasPath(ConfigHandler.indexFactoryClass) ?
                getIndexConfig(indexID).getString(ConfigHandler.indexFactoryClass) :
                null;
    }

    public String defaultIndicesFactoryClass() {
        return getDefaultsConfig().getString(defaultIndicesFactoryClass);
    }

    public String indexSearcher(IndexID indexID) {
        return getIndexConfig(indexID).hasPath(indexSearcher) ?
                getIndexConfig(indexID).getString(indexSearcher) : null;
    }

    public String defaultIndexSearcher(IndexID indexID) {
        return getDefaultsConfig().hasPath(String.format(defaultIndexSearcher, indexID.getIndex())) ?
                getDefaultsConfig().getString(String.format(defaultIndexSearcher, indexID.getIndex())) : null;
    }

    public String fallbackIndexSearcher() {
        return getDefaultsConfig().hasPath(indicesFallbackSearcher) ?
                getDefaultsConfig().getString(indicesFallbackSearcher) : null;
    }

    public String dbClass(String kbName) {
        return getKBConfig(kbName).hasPath(dbImplPath) ? getKBConfig(kbName).getString(dbImplPath) : null;
    }

    public String dbType(String kbName) {
        return getKBConfig(kbName).hasPath(dbTypePath) ? getKBConfig(kbName).getString(dbTypePath) : null;
    }

    public String defaultDBClass(DBType dbType) {
        return getDefaultsConfig().getString(defaultDBClass(dbType.toString().toLowerCase()));
    }

    public boolean logStats() {
        return getMainConfig().getBoolean("progress-watcher.log-stats");
    }

    public int elasticBulkConcurrency(IndexID indexID) {
        return getIndexConfig(indexID).getInt(elasticBulkConcurrency);
    }

    public int elasticBulkActions(IndexID indexID) {
        return getIndexConfig(indexID).getInt(elasticBulkActions);
    }

    public String language(String kbName) {
        return getKBConfig(kbName).hasPath(language) ? getKBConfig(kbName).getString(language) : null;
    }
    public String defaultLanguage() {
        return getDefaultsConfig().getString(language);
    }

    public Set<String> indices(String kbName) {
        ConfigObject typeObj = getKBConfig(kbName).getObject(index);
        return typeObj.keySet();
    }

    public boolean useIndex(String kbName) {
        return getKBConfig(kbName).hasPath(hdtUseIndex) && getKBConfig(kbName).getBoolean(hdtUseIndex);
    }

    public String hdtFile(String kbName) {
        return getKBConfig(kbName).hasPath(hdtFile) ? getKBConfig(kbName).getString(hdtFile) : null;
    }

    public String composedOf(String kbName) {
        return getKBConfig(kbName).hasPath(composedOf) ? getKBConfig(kbName).getString(composedOf) : null;
    }

}
