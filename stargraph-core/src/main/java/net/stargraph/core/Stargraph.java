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

    private ConfigHandler cfg;
    private String dataRootDir;
    private IndexFactory indexFactory;
    private DatabaseFactory databaseFactory;
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
     * @param config  Configuration instance.
     * @param initKBs Controls the startup behaviour. Use <code>false</code> to postpone KB specific initialization.
     */
    public Stargraph(Config config, boolean initKBs) {
        logger.info(marker, "Memory: {}", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage());
        this.cfg = new ConfigHandler(config);
        logger.trace(marker, "Configuration: {}", ModelUtils.toStr(config));
        // Only KBs in this set will be initialized. Unit tests appreciates!
        this.kbInitSet = new LinkedHashSet<>();
        this.knowledgeBases = new ConcurrentHashMap<>(8);
        this.robust = cfg.isRobust();


        // Configurable defaults
        setDataRootDir(cfg.dataRootDir()); // absolute path is expected

        setDefaultIndexFactory(createDefaultIndicesFactory());

        setDefaultDatabaseFactory(createDatabaseFactory(DBType.Graph));

        if (initKBs) {
            initialize();
        }
    }

    public synchronized final void initialize() {
        if (initialized) {
            throw new IllegalStateException("Core already initialized.");
        }

        this.initializeKBs();

        logger.info(marker, "Data root directory: '{}'", getDataRootDir());
        logger.info(marker, "Default Store Factory: '{}'", indexFactory.getClass().getName());
        logger.info(marker, "DS Service Endpoint: '{}'", cfg.distServiceUrl());
        logger.info(marker, "★☆ {}, {} ({}) ★☆", Version.getCodeName(), Version.getBuildVersion(), Version.getBuildNumber());
        initialized = true;
    }


    /**
     * This function initializes the knowledge bases, i.e. creates the corresponding {@link KnowledgeBase} classes.
     * <p>
     * The information which KBs to initialize is either configured via the {@link Stargraph#kbInitSet} attribute
     * (and its corresponding setter) or is loaded via the config. Every entry by the key kb is considered.
     */
    private void initializeKBs() {
        if (!kbInitSet.isEmpty()) {
            // stuff is set programmatically
            logger.info(marker, "KB init set: {}", kbInitSet);
            kbInitSet.forEach(this::initializeKB);
        } else {
            // from config
            cfg.knowledgeBases().forEach(this::initializeKB);
        }
    }


    /**
     * Initializes (i.e. creates the {@link KnowledgeBase} class and calls {@link KnowledgeBase#initialize()})
     * a knowledge base.
     *
     * @param kbName Name of the Knowledge base.
     */
    private void initializeKB(String kbName) {
        if (cfg.isEnabled(kbName)) {
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


    public void setKBInitSet(String... kbNames) {
        this.kbInitSet.addAll(Arrays.asList(kbNames));
    }


    public KnowledgeBase getKnowledgeBase(String dbId) {
        if (knowledgeBases.containsKey(dbId)) {
            return knowledgeBases.get(dbId);
        }
        throw new StarGraphException("KB not found: '" + dbId + "'");
    }

    public Index getIndex(IndexID id) {
        return getKnowledgeBase(id.getKnowledgeBase()).getIndex(id.getIndex());
    }

    public Collection<KnowledgeBase> getKBs() {
        return knowledgeBases.values();
    }

    public boolean hasKB(String kbName) {
        return getKBs().stream().anyMatch(core -> core.getName().equals(kbName));
    }

    public String getDataRootDir() {
        return dataRootDir;
    }

    public IndexPopulator getIndexer(IndexID indexID) {
        return getKnowledgeBase(indexID.getKnowledgeBase()).getIndexPopulator(indexID.getIndex());
    }

    public IndexSearchExecutor getSearcher(IndexID indexID) {
        return getKnowledgeBase(indexID.getKnowledgeBase()).getSearchExecutor(indexID.getIndex());
    }

    /* Default setter functions to configure StarGraph programmatically instead of using a config. */
    public void setDataRootDir(String dataRootDir) {
        this.dataRootDir = Objects.requireNonNull(dataRootDir);
    }

    public void setDataRootDir(File dataRootDir) {
        this.dataRootDir = Objects.requireNonNull(dataRootDir.getAbsolutePath());
    }

    public void setDefaultIndexFactory(IndexFactory indexFactory) {
        this.indexFactory = Objects.requireNonNull(indexFactory);
    }

    public void setDefaultDatabaseFactory(DatabaseFactory databaseFactory) {
        this.databaseFactory = Objects.requireNonNull(databaseFactory);
    }

    public ConfigHandler getConfig() {
        return this.cfg;
    }

    /**
     * Creates a processor chain for an index.
     * <p>
     * Does so from the config file. Mainly to be used when populating an index.
     *
     * @param indexID Index id of for which the processor chain is to be created.
     * @return {@link ProcessorChain} of processors configured via the config for a given index.
     */
    public ProcessorChain createProcessorChain(IndexID indexID) {
        List<? extends Config> processorsCfg = cfg.getProcessorsCfg(indexID);
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

    /**
     * Creates a data provider for a given index from the config file.
     * <p>
     * The data provider is used to yield data when populating an index.
     *
     * @param indexID ID of the index for which to provide data.
     * @return Data provider for a given index.
     */
    public DataProvider<? extends Holder> createDataProvider(IndexID indexID) {
        DataProviderFactory factory = this.createDataProviderFactory(indexID);

        DataProvider<? extends Holder> provider = factory.create(indexID);

        if (provider == null) {
            throw new IllegalStateException("DataProvider not created!");
        }

        logger.info(marker, "Creating {} data provider", indexID);
        return provider;

    }

    /**
     * Creates a data provider factory from the config file for a given index.
     *
     * @param indexID
     * @return
     */
    private DataProviderFactory createDataProviderFactory(IndexID indexID) {
        DataProviderFactory factory;

        try {
            String className = cfg.dataProviderClassName(indexID);

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


    public synchronized final void terminate() {
        if (!initialized) {
            throw new IllegalStateException("Not initialized");
        }

        knowledgeBases.values().forEach(KnowledgeBase::terminate);
        initialized = false;
    }

    IndexFactory createIndexFactoryForID(IndexID indexID) {
        if (indexID != null) {
            //from index configuration

            String className = cfg.indexFactoryClass(indexID);
            if (className != null) {
                logger.info(marker, "Using '{}'.", className);
                return createIndicesFactory(className);
            }
        }

        if (indexFactory == null) {
            logger.info("No default index factory set, reading from main config...");
            // from main configuration if not already set

            indexFactory = createIndicesFactory(cfg.defaultIndicesFactoryClass());
        }

        return indexFactory;
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

    @SuppressWarnings("unchecked")
    public Class<? extends IndexSearcher> getIndexSearcherType(IndexID indexID) {
        // either concrete implementation is given
        // or an interface
        // or fallback to default interface
        String clsName = cfg.indexSearcher(indexID);
        if (clsName == null) {
            logger.debug(marker, "No explicit index searcher class defined, using default for this index.");
            clsName = cfg.defaultIndexSearcher(indexID);
        }
        if (clsName == null) {
            logger.info(marker, "No default index searcher for this index defined. Using fallback.");
            clsName = cfg.fallbackIndexSearcher();
        }
        if (clsName == null) {
            throw new StargraphConfigurationException("Something somewhere went terribly wrong!");
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
        return createIndexFactoryForID(null);
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

    /**
     * Creates a database factory for a given knowledge base.
     * <p>
     * The factory is attempted to be red from the config file
     * <ul>
     * <li>either a concrete implementation under the path {@link ConfigHandler#dbImplPath}</li>
     * <li>or at the type of the DB under the path {@link ConfigHandler#dbTypePath}</li>
     * </ul>
     * If neither is specified, defaults to the database factory of type defined in the defaults section.
     *
     * @param kbName
     * @return
     */
    DatabaseFactory createDatabaseFactoryForKB(String kbName) {


        String dbClass = cfg.dbClass(kbName);

        if (dbClass != null) {
            // explicit class name is given, all is good
            logger.info(marker, "using '{}'", dbClass);
            return createDatabaseFactory(dbClass);
        }
        String dbType = cfg.dbType(kbName);
        if (dbType != null) {
            // at least type is given, fair enough, creating default for type
            if (DBType.contains(dbType)) {
                return createDatabaseFactory(DBType.valueOf(dbType));

            }
            // unimplemented type... defaulting to graph
            logger.warn(marker, "Unknown database type '{}' for knowledge base '{}'.", dbType, kbName);
        }


        //neither explicit class name nor type is given. returning the default one
        logger.warn(marker, "Neither the type nor a factory implementation for the knowledge base" +
                "'{}' was configured! Returning default database Factory!", kbName);
        if (databaseFactory == null) {
            logger.info(marker, "No default database factory set! Creating default Graph database!");
            databaseFactory = createDatabaseFactory(DBType.Graph);
        }
        return databaseFactory;
    }

    /**
     * Creates a database factory from a given class name.
     *
     * @param className Name of the database factory class.
     * @return {@link DatabaseFactory}
     */
    private DatabaseFactory createDatabaseFactory(String className) {
        try {
            Class<?> providerClazz = Class.forName(className);
            Constructor<?> constructor = providerClazz.getConstructors()[0];
            return (DatabaseFactory) constructor.newInstance(this);
        } catch (Exception e) {
            throw new StarGraphException("Couldn't initialize database!.", e);
        }
    }

    /**
     * Creates a default (according to reference.conf) database Factory for a given type.
     *
     * @param dbType Type of the database factory.
     * @return DatabaseFactory.
     */
    private DatabaseFactory createDatabaseFactory(DBType dbType) {
        String clsName = cfg.defaultDBClass(dbType);
        logger.info(marker, "Creating Database factory {}", clsName);
        return createDatabaseFactory(clsName);
    }
}
