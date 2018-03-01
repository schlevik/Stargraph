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
import net.stargraph.core.index.IndexPopulator;
import net.stargraph.core.processors.Processors;
import net.stargraph.core.search.executor.IndexSearchExecutor;
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
    private IndicesFactory indicesFactory;
    private GraphModelFactory graphModelFactory;
    private Map<String, KnowledgeBase> kbCoreMap;
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
        this.kbCoreMap = new ConcurrentHashMap<>(8);

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
        if (kbCoreMap.containsKey(dbId)) {
            return kbCoreMap.get(dbId);
        }
        throw new StarGraphException("KB not found: '" + dbId + "'");
    }

    public Config getMainConfig() {
        return mainConfig;
    }

    public Config getKBConfig(String kbName) {
        return mainConfig.getConfig(String.format("kb.%s", kbName));
    }

    public Config getModelConfig(IndexID indexID) {
        return mainConfig.getConfig(indexID.getModelPath());
    }

    public Collection<KnowledgeBase> getKBs() {
        return kbCoreMap.values();
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

    public void setDefaultIndicesFactory(IndicesFactory indicesFactory) {
        this.indicesFactory = Objects.requireNonNull(indicesFactory);
    }

    public void setDefaultGraphModelFactory(GraphModelFactory modelFactory) {
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
        logger.info(marker, "Default Store Factory: '{}'", indicesFactory.getClass().getName());
        logger.info(marker, "DS Service Endpoint: '{}'", mainConfig.getString("distributional-service.rest-url"));
        logger.info(marker, "★☆ {}, {} ({}) ★☆", Version.getCodeName(), Version.getBuildVersion(), Version.getBuildNumber());
        initialized = true;
    }

    public synchronized final void terminate() {
        if (!initialized) {
            throw new IllegalStateException("Not initialized");
        }

        kbCoreMap.values().forEach(KnowledgeBase::terminate);
        initialized = false;
    }

    IndicesFactory getIndicesFactory(IndexID indexID) {
        final String idxStorePath = "index-store.factory.class";
        if (indexID != null) {
            //from model configuration
            Config modelCfg = getModelConfig(indexID);
            if (modelCfg.hasPath(idxStorePath)) {
                String className = modelCfg.getString(idxStorePath);
                logger.info(marker, "Using '{}'.", className);
                return createIndicesFactory(className);
            }
        }

        if (indicesFactory == null) {
            //from main configuration if not already set
            indicesFactory = createIndicesFactory(getMainConfig().getString(idxStorePath));
        }

        return indicesFactory;
    }

    GraphModelFactory getGraphModelFactory(String kbName) {
        final String idxStorePath = "graph-model.factory.class";
        if (kbName != null) {
            //from model configuration
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
                kbCoreMap.put(kbName, new KnowledgeBase(kbName, this, true));
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
        String path = String.format("%s.processors", indexID.getModelPath());
        if (mainConfig.hasPath(path)) {
            return mainConfig.getConfigList(path);
        }
        return null;
    }

    private Config getDataProviderCfg(IndexID indexID) {
        String path = String.format("%s.provider", indexID.getModelPath());
        return mainConfig.getConfig(path);
    }

    private IndicesFactory createDefaultIndicesFactory() {
        return getIndicesFactory(null);
    }

    private GraphModelFactory createDefaultGraphModelFactory() {
        return getGraphModelFactory(null);
    }

    private IndicesFactory createIndicesFactory(String className) {
        try {
            Class<?> providerClazz = Class.forName(className);
            Constructor<?> constructor = providerClazz.getConstructors()[0];
            return (IndicesFactory) constructor.newInstance();
        } catch (Exception e) {
            throw new StarGraphException("Can't initialize indexers.", e);
        }
    }

    private GraphModelFactory createGraphModelFactory(String className) {
        try {
            Class<?> providerClazz = Class.forName(className);
            Constructor<?> constructor = providerClazz.getConstructors()[0];
            return (GraphModelFactory) constructor.newInstance(this);
        } catch (Exception e) {
            throw new StarGraphException("Can't initialize graph model.", e);
        }
    }

}
