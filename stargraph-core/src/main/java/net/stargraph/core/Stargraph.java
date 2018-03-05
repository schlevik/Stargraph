package net.stargraph.core;

/*-
 * ==========================License-Start=============================
 * stargraph-core
 * --------------------------------------------------------------------
 * Copyright (C) 2017 Lambda^3
 * --------------------------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * ==========================License-End===============================
 */

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.stargraph.ModelUtils;
import net.stargraph.StarGraphException;
import net.stargraph.StargraphConfigurationException;
import net.stargraph.core.index.IndexPopulator;
import net.stargraph.core.processors.Processors;
import net.stargraph.core.search.database.DBType;
import net.stargraph.core.search.database.DatabaseFactory;
import net.stargraph.core.search.executor.IndexSearchExecutor;
import net.stargraph.core.search.index.IndexSearcher;
import net.stargraph.data.DataProvider;
import net.stargraph.data.DataProviderFactory;
import net.stargraph.data.processor.Holder;
import net.stargraph.data.processor.Processor;
import net.stargraph.data.processor.ProcessorChain;
import net.stargraph.model.IndexID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Stargraph database core implementation.
 */
public final class Stargraph {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker = MarkerFactory.getMarker("stargraph");

    private Config mainConfig;
    private String dataRootDir;
    private IndexFactory indexFactory;
    private JenaDatabaseFactory graphModelFactory;
    private Map<String, KnowledgeBase> knowledgeBases;
    private Set<String> kbInitSet;
    private boolean initialized;

    /**
     * Is used at some points to distinguish whether an exception should end the program or be
     * ignored for the sake of robustness.
     */
    private boolean robust = true;

    /**
     * Constructs a new Stargraph core API entry-point.
     */
    public Stargraph() {
        this(ConfigFactory.load().getConfig("stargraph"), true);
    }

    /**
     * Constructs a new Stargraph core API entry-point.
     *
     * @param cfg     Configuration instance.
     * @param initKBs Controls the startup behaviour. Use <code>false</code> to postpone KB specific initialization.
     */
    public Stargraph(Config cfg, boolean initKBs) {
        logger.info(marker, "Memory: {}", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage());
        this.mainConfig = Objects.requireNonNull(cfg);
        logger.trace(marker, "Configuration: {}", ModelUtils.toStr(mainConfig));
        // Only KBs in this set will be initialized. Unit tests appreciates!
        this.kbInitSet = new LinkedHashSet<>();
        this.knowledgeBases = new ConcurrentHashMap<>(8);

        this.robust = mainConfig.getBoolean("robust");


        // Configurable defaults
        setDataRootDir(mainConfig.getString("data.root-dir")); // absolute path is expected
        setDefaultIndicesFactory(createDefaultIndicesFactory());
        setDefaultGraphModelFactory(createDefaultGraphModelFactory());

        if (initKBs) {
            initialize();
        }
    }

    public KnowledgeBase getKBCore(String dbId) {
        if (knowledgeBases.containsKey(dbId)) {
            return knowledgeBases.get(dbId);
        }
        throw new StarGraphException("KB not found: '" + dbId + "'");
    }

    public Index getIndex(IndexID id) {
        return getKBCore(id.getKnowledgeBase()).getIndex(id.getIndex());
    }

    public Config getMainConfig() {
        return mainConfig;
    }

    public Config getKBConfig(String kbName) {
        return getKBCore(kbName).getConfig();
    }

    public Config getIndexConfig(IndexID indexID) {
        return mainConfig.getConfig(indexID.getIndexPath());
    }

    public Collection<KnowledgeBase> getKBs() {
        return knowledgeBases.values();
    }

    public boolean hasKB(String kbName) {
        return getKBs().stream().anyMatch(core -> core.getKBName().equals(kbName));
    }

    public String getDataRootDir() {
        return dataRootDir;
    }

    public IndexPopulator getIndexer(IndexID indexID) {
        return getKBCore(indexID.getKnowledgeBase()).getIndexPopulator(indexID.getIndex());
    }

    public IndexSearchExecutor getSearcher(IndexID indexID) {
        return getKBCore(indexID.getKnowledgeBase()).getSearchExecutor(indexID.getIndex());
    }

    public void setKBInitSet(String... kbIds) {
        this.kbInitSet.addAll(Arrays.asList(kbIds));
    }

    public void setDataRootDir(String dataRootDir) {
        this.dataRootDir = Objects.requireNonNull(dataRootDir);
    }

    public void setDataRootDir(File dataRootDir) {
        this.dataRootDir = Objects.requireNonNull(dataRootDir.getAbsolutePath());
    }

    public void setDefaultIndicesFactory(IndexFactory indexFactory) {
        this.indexFactory = Objects.requireNonNull(indexFactory);
    }

    public void setDefaultGraphModelFactory(JenaDatabaseFactory modelFactory) {
        this.graphModelFactory = Objects.requireNonNull(modelFactory);
    }

    public ProcessorChain createProcessorChain(IndexID indexID) {
        List<? extends Config> processorsCfg = getProcessorsCfg(indexID);
        if (processorsCfg != null && processorsCfg.size() != 0) {
            List<Processor> processors = new ArrayList<>();
            processorsCfg.forEach(config -> processors.add(Processors.create(this, config)));
            ProcessorChain chain = new ProcessorChain(processors);
            logger.info(marker, "processors = {}", chain);
            return chain;
        }
        logger.warn(marker, "No processors configured for {}", indexID);
        return null;
    }

    public DataProvider<? extends Holder> createDataProvider(IndexID indexID) {
        DataProviderFactory factory = this.createDataProviderFactory(indexID);

        DataProvider<? extends Holder> provider = factory.create(indexID);

        if (provider == null) {
            throw new IllegalStateException("DataProvider not created!");
        }

        logger.info(marker, "Creating {} data provider", indexID);
        return provider;

    }

    public DataProviderFactory createDataProviderFactory(IndexID indexID) {
        DataProviderFactory factory;

        try {
            String className = getDataProviderCfg(indexID).getString("class");
            Class<?> providerClazz = Class.forName(className);
            Constructor[] constructors = providerClazz.getConstructors();

            if (BaseDataProviderFactory.class.isAssignableFrom(providerClazz)) {
                // It's our internal factory hence we inject the core dependency.
                factory = (DataProviderFactory) constructors[0].newInstance(this);
            } else {
                // This should be a user factory without constructor.
                // API user should rely on configuration or other means to initialize.
                // See TestDataProviderFactory as an example
                factory = (DataProviderFactory) providerClazz.newInstance();
            }
            return factory;
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException | ClassNotFoundException e) {
            throw new StarGraphException("Failed to Create data provider factory: " + indexID, e);
        }
    }


    public synchronized final void initialize() {
        if (initialized) {
            throw new IllegalStateException("Core already initialized.");
        }

        this.initializeKBs();

        logger.info(marker, "Data root directory: '{}'", getDataRootDir());
        logger.info(marker, "Default Store Factory: '{}'", indexFactory.getClass().getName());
        logger.info(marker, "DS Service Endpoint: '{}'", mainConfig.getString("distributional-service.rest-url"));
        logger.info(marker, "★☆ {}, {} ({}) ★☆", Version.getCodeName(), Version.getBuildVersion(), Version.getBuildNumber());
        initialized = true;
    }

    public synchronized final void terminate() {
        if (!initialized) {
            throw new IllegalStateException("Not initialized");
        }

        knowledgeBases.values().forEach(KnowledgeBase::terminate);
        initialized = false;
    }

    IndexFactory getIndicesFactory(IndexID indexID) {
        final String idxStorePath = "index-store.factory.class";
        if (indexID != null) {
            //from index configuration
            Config modelCfg = getIndexConfig(indexID);
            if (modelCfg.hasPath(idxStorePath)) {
                String className = modelCfg.getString(idxStorePath);
                logger.info(marker, "Using '{}'.", className);
                return createIndicesFactory(className);
            }
        }

        if (indexFactory == null) {
            //from main configuration if not already set
            indexFactory = createIndicesFactory(getMainConfig().getString(idxStorePath));
        }

        return indexFactory;
    }

    DatabaseFactory getDatabaseFactory(String kbName) {
        final String dbImplPath = "db.class";
        final String dbTypePath = "db.type";
        Config kbCfg = getKBConfig(kbName);
        if (kbCfg.hasPath(dbImplPath)) {
            String className = kbCfg.getString(dbImplPath);
            logger.info(marker, "using '{}'", className);
            return createDatabaseFactory(className);
        } else if (kbCfg.hasPath(dbTypePath)) {
            String typeName = kbCfg.getString(dbTypePath);
            DBType type;
            if (DBType.contains(typeName)) {
                type = DBType.valueOf(typeName);
            } else {
                logger.warn(marker, "Unknown database type '{}' for knowledge base '{}'. Assuming Graph.", typeName, kbName);
                type = DBType.Graph;
            }

            return createDatabaseFactory(type);
        }
        logger.warn(marker, "Neither the type nor a factory implementation for the knowledge" +
                "base '{}' was configured! Assuming type=Graph", kbName);
        return createDatabaseFactory(DBType.Graph);
    }


    JenaDatabaseFactory getGraphModelFactory(String kbName) {
        final String idxStorePath = "graph-model.factory.class";
        if (kbName != null) {
            //from index configuration
            Config kbCfg = getKBConfig(kbName);
            if (kbCfg.hasPath(idxStorePath)) {
                String className = kbCfg.getString(idxStorePath);
                logger.info(marker, "Using '{}'.", className);
                return createGraphModelFactory(className);
            }
        }

        if (graphModelFactory == null) {
            //from main configuration if not already set
            graphModelFactory = createGraphModelFactory(getMainConfig().getString(idxStorePath));
        }

        return graphModelFactory;
    }

    /**
     * This checks whether the KB is enabled.
     * <p>
     * Checks before the KB is initialized that's why {@link KnowledgeBase#getConfig()} is not used.
     *
     * @param kbName Name of kb to check.
     * @return <code>true</code> if enabled, <code>false</code> otherwise.
     */
    private boolean isEnabled(String kbName) {
        Config kbConfig = mainConfig.getConfig(String.format("kb.%s", kbName));
        return kbConfig.getBoolean("enabled");
    }

    /**
     * This function initializes the knowledge bases, i.e. creates the corresponding {@link KnowledgeBase} classes.
     * <p>
     * The information which KBs to initialize is either configured via the {@link Stargraph#kbInitSet} attribute
     * (and its corresponding setter) or is loaded via the config. Every entry by the key kb is considered.
     */
    private void initializeKBs() {
        if (!kbInitSet.isEmpty()) {
            logger.info(marker, "KB init set: {}", kbInitSet);
            kbInitSet.forEach(this::initializeKB);
        } else {
            if (mainConfig.hasPathOrNull("kb")) {
                if (mainConfig.getIsNull("kb")) {
                    throw new StarGraphException("No KB configured.");
                }

                mainConfig.getObject("kb").keySet().forEach(this::initializeKB);
            } else {
                throw new StarGraphException("No KBs configured.");
            }
        }
    }

    private void initializeKB(String kbName) {
        if (isEnabled(kbName)) {
            try {
                knowledgeBases.put(kbName, new KnowledgeBase(kbName, this, true));
            } catch (Exception e) {
                logger.error(marker, "Error starting '{}'", kbName, e);
                if (!robust) {
                    throw e;
                }
            }
        } else {
            logger.warn(marker, "KB '{}' is disabled", kbName);
        }
    }

    private List<? extends Config> getProcessorsCfg(IndexID indexID) {
        String path = String.format("%s.processors", indexID.getIndexPath());
        if (mainConfig.hasPath(path)) {
            return mainConfig.getConfigList(path);
        }
        return null;
    }

    private Config getDataProviderCfg(IndexID indexID) {
        String path = String.format("%s.provider", indexID.getIndexPath());
        return mainConfig.getConfig(path);
    }

    public IndexSearcher createIndexSearcher(Class<? extends IndexSearcher> cls, Index index) {
        logger.debug(marker, "Creating index searcher '{}' for {}", cls.getName(), index.getID());
        if (!(cls.getConstructors().length > 0)) {
            throw new StarGraphException("Implementation error in " + cls + "! First constructor should be public!");
        }
        Constructor<?> constructor = cls.getConstructors()[0];

        try {
            return (IndexSearcher) constructor.newInstance(index);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new StarGraphException("Couldn't create index searcher for " + index.getID() + " !", e);
        }

    }

    public Class<? extends IndexSearcher> getIndexSearcherType(IndexID indexID) {
        // either concrete implementation is given
        // or an interface
        // or fallback to default interface
        Config indexConfig = getIndexConfig(indexID);
        String clsName;
        if (indexConfig.hasPath("searcher")) {
            clsName = indexConfig.getString("searcher");
        } else {
            clsName = getMainConfig().getString(String.format("default.%s.searcher", indexID.getIndex()));
        }
        try {
            Class<?> iFace = Class.forName(clsName);
            return (Class<? extends IndexSearcher>) iFace;
        } catch (ClassNotFoundException e) {
            throw new StargraphConfigurationException(
                    "Neither the index nor the default section defined a searcher for the index " + indexID.getIndex() +
                            "!");
        } catch (ClassCastException e) {
            throw new StargraphConfigurationException("Index searcher for " + indexID.getIndex() +
                    " must inherit from IndexSearcher!");
        }
    }

    private IndexFactory createDefaultIndicesFactory() {
        return getIndicesFactory(null);
    }

    private JenaDatabaseFactory createDefaultGraphModelFactory() {
        return getGraphModelFactory(null);
    }

    private IndexFactory createIndicesFactory(String className) {
        try {
            Class<?> providerClazz = Class.forName(className);
            Constructor<?> constructor = providerClazz.getConstructors()[0];

            return (IndexFactory) constructor.newInstance();
        } catch (Exception e) {
            throw new StarGraphException("Can't initialize indexers.", e);
        }
    }

    private JenaDatabaseFactory createGraphModelFactory(String className) {
        try {
            Class<?> providerClazz = Class.forName(className);
            Constructor<?> constructor = providerClazz.getConstructors()[0];
            return (JenaDatabaseFactory) constructor.newInstance(this);
        } catch (Exception e) {
            throw new StarGraphException("Can't initialize graph index.", e);
        }
    }

    private DatabaseFactory createDatabaseFactory(String className) {
        try {
            Class<?> providerClazz = Class.forName(className);
            Constructor<?> constructor = providerClazz.getConstructors()[0];
            return (DatabaseFactory) constructor.newInstance(this);
        } catch (Exception e) {
            throw new StarGraphException("Couldn't initialize database!.", e);
        }
    }

    private DatabaseFactory createDatabaseFactory(DBType dbType) {
        String clsName = getMainConfig().getString(String.format("default.db.%s.class", dbType.toString().toLowerCase()));
        return createDatabaseFactory(clsName);
    }
}
