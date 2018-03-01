package net.stargraph.core;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import net.stargraph.StarGraphException;
import net.stargraph.StargraphConfigurationException;
import net.stargraph.core.search.database.*;
import net.stargraph.core.impl.corenlp.NERSearcher;
import net.stargraph.core.impl.jena.JenaGraphSearcher;
import net.stargraph.core.index.IndexPopulator;
import net.stargraph.core.ner.NER;
import net.stargraph.core.search.executor.BaseIndexSearchExecutor;
import net.stargraph.core.search.executor.IndexSearchExecutor;
import net.stargraph.core.search.index.DocumentIndexSearcher;
import net.stargraph.core.search.index.EntityIndexSearcher;
import net.stargraph.core.search.index.IndexSearcher;
import net.stargraph.data.DataProvider;
import net.stargraph.data.DataProviderFactory;
import net.stargraph.data.processor.Holder;
import net.stargraph.model.IndexID;
import net.stargraph.query.Language;
import net.stargraph.rank.ModifiableIndraParams;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * An instance of a Knowledge Base and its inner core components. What else could be?
 */
public final class KnowledgeBase {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker;

    private String kbName;
    private Config mainConfig;
    private Config kbConfig;
    private Language language;
    private KBLoader kbLoader;
    private Model graphModel;
    private Namespace namespace;
    private Stargraph stargraph;
    private NER ner;
    private Map<String, IndexPopulator> indexPopulators;
    private Map<String, IndexSearchExecutor> searchExecutors;
    private boolean running;

    //
    private Map<String, Index> indices;
    private Database database;

    public KnowledgeBase(String kbName, Stargraph stargraph, boolean start) {
        this.kbName = Objects.requireNonNull(kbName);
        this.stargraph = Objects.requireNonNull(stargraph);
        this.mainConfig = stargraph.getMainConfig();
        this.kbConfig = mainConfig.getConfig(String.format("kb.%s", kbName));
        this.marker = MarkerFactory.getMarker(kbName);
        this.indexPopulators = new ConcurrentHashMap<>();
        this.searchExecutors = new ConcurrentHashMap<>();
        this.language = Language.valueOf(kbConfig.getString("language").toUpperCase());
        this.namespace = Namespace.create(kbConfig);

        //
        this.indices = new ConcurrentHashMap<>();

        if (start) {
            initialize();
        }
    }

    public synchronized void initialize() {
        if (running) {
            throw new IllegalStateException("Already started.");
        }

        final List<String> indexNames = getIndexIDs().stream().map(IndexID::getIndex).collect(Collectors.toList());

        for (String modelId : indexNames) {
            logger.info(marker, "Initializing '{}'", modelId);
            final IndexID indexID = IndexID.of(kbName, modelId);
            IndicesFactory factory = stargraph.getIndicesFactory(indexID);

            IndexPopulator indexPopulator = factory.createIndexer(indexID, stargraph);

            if (indexPopulator != null) {
                indexPopulator.start();
                indexPopulators.put(modelId, indexPopulator);
            } else {
                logger.warn(marker, "No indexPopulator created for {}", indexID);
            }

            BaseIndexSearchExecutor searcher = factory.createSearcher(indexID, stargraph);

            if (searcher != null) {
                searcher.start();
                searchExecutors.put(modelId, searcher);
            } else {
                logger.warn(marker, "No searcher created for {}", indexID);
            }
        }

        this.kbLoader = new KBLoader(this);
        this.running = true;
    }


    public <T extends IndexSearcher> T getIndexSearcher(IndexID id) {
        try {
            return (T) this.getIndex(id).getSearcher();
        } catch (ClassCastException e) {
            throw new StarGraphException("Cast error!");
        }
    }

    public Index getIndex(IndexID id) {
        Index index = this.indices.get(id.getIndex());
        if (index == null) {
            if (isComposed()) {
                return composedOf().getIndex(id);
            } else
                throw new StargraphConfigurationException(String.format(
                        "The index %s is not configured for Knowledge base %s.",
                        id.getKnowledgeBase(),
                        id.getIndex())
                );
        } else {
            return index;
        }
    }

    public DatabaseResult executeDatabaseQuery(DatabaseQuery<?> query) {
        Database db = this.getDatabase(query.getType());
        return db.query(query);

    }

    private Database getDatabase(DBType type) {
        if (this.database.getType() == type) {
            return this.database;
        } else {
            if (isComposed()) {
                return composedOf().getDatabase(type);
            } else {
                throw new StargraphConfigurationException(String.format("No database for type %s configured for knowledge base %s.",
                        type,
                        kbName));
            }
        }
    }

    private boolean isComposed() {
        //TODO
        return false;
    }

    private KnowledgeBase composedOf() {
        //TODO
        return null;
    }

    public synchronized void terminate() {
        if (!running) {
            throw new IllegalStateException("Already stopped.");
        }
        logger.info(marker, "Terminating '{}'", kbName);

        indexPopulators.values().forEach(IndexPopulator::stop);
        searchExecutors.values().forEach(IndexSearchExecutor::stop);

        this.running = false;
    }

    public Config getConfig() {
        return kbConfig;
    }

    public Config getConfig(String path) {
        return kbConfig.getConfig(path);
    }

    public String getKBName() {
        return kbName;
    }

    public Language getLanguage() {
        return language;
    }

    public List<IndexID> getIndexIDs() {
        ConfigObject typeObj = this.mainConfig.getObject(String.format("kb.%s.model", kbName));
        return typeObj.keySet().stream().map(modelName -> IndexID.of(kbName, modelName)).collect(Collectors.toList());
    }

    public Model getGraphModel() {
        checkRunning();
        return stargraph.getGraphModelFactory(kbName).getModel(kbName);
    }

    public IndexPopulator getIndexPopulator(String modelId) {
        checkRunning();
        if (indexPopulators.containsKey(modelId)) {
            return indexPopulators.get(modelId);
        }

        throw new StarGraphException("IndexPopulator not found nor initialized: " + IndexID.of(kbName, modelId));
    }

    public IndexSearchExecutor getSearchExecutor(String modelId) {
        checkRunning();
        if (searchExecutors.containsKey(modelId)) {
            return searchExecutors.get(modelId);
        }
        throw new StarGraphException("Searcher not found nor initialized: " + IndexID.of(kbName, modelId));
    }

    public KBLoader getLoader() {
        checkRunning();
        return kbLoader;
    }

    public NER getNER() {
        // lazy creation
        if (ner == null) {
            this.ner = new NERSearcher(language, createEntitySearcher(), kbName);
        }
        return this.ner;
    }

    public Namespace getNamespace() {
        return namespace;
    }

    public EntityIndexSearcher createEntitySearcher() {
        return createEntitySearcher("entities");
    }

    public EntityIndexSearcher createEntitySearcher(String index) {
        IndicesFactory factory = stargraph.getIndicesFactory(IndexID.of(kbName, index));
        return factory.createEntitySearcher(this);
    }

    public DocumentIndexSearcher createDocumentSearcher() {
        IndicesFactory factory = stargraph.getIndicesFactory(IndexID.of(kbName, "documents"));
        return factory.createDocumentSearcher(this);
    }

    public GraphSearcher createGraphSearcher() {
        return new JenaGraphSearcher(kbName, stargraph);
    }

    public void configureDistributionalParams(ModifiableIndraParams params) {
        String indraUrl = stargraph.getMainConfig().getString("distributional-service.rest-url");
        String indraCorpus = stargraph.getMainConfig().getString("distributional-service.corpus");
        params.url(indraUrl).corpus(indraCorpus).language(language.code);
    }

    private void checkRunning() {
        if (!running) {
            throw new IllegalStateException("KB Core not started.");
        }
    }

    public void extend(List<Statement> data) {
        // TODO: 1/5/18 This is bound to JENA/RDF atm
        /*
         * Main idea:
         * 1) add all statements to graph V
         * 2) get all indexPopulators for this KB
         * 3) for each indexer, index newly incoming data
         * 4) uuuuh yea, sth like that
         */
        logger.trace("Entering extend");
        Model model = getGraphModel();
        logger.debug("Adding incoming data to graph.");
        model.add(data);


        for (IndexID indexID : this.getIndexIDs()) {
            logger.debug("For KBid {}...", indexID);
            // create <? extends Holder> from List of statements.
            IndexPopulator indexer = indexPopulators.get(indexID.getIndex());
            logger.debug("Got indexer of type {}", indexer.getClass());
            // create dataProvider from given model
            DataProviderFactory dataProviderFactory = stargraph.createDataProviderFactory(indexID);
            logger.debug("Created DataProviderFactory of type {}", dataProviderFactory.getClass());
            DataProvider<? extends Holder> dataProvider = dataProviderFactory.create(indexID, data);
            logger.debug("Created DataProvider of type {}", dataProvider.getClass());
            indexer.extend(dataProvider);
        }
    }
}
