package net.stargraph.core;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import net.stargraph.StarGraphException;
import net.stargraph.core.search.GraphSearcher;
import net.stargraph.core.impl.corenlp.NERSearcher;
import net.stargraph.core.impl.jena.JenaGraphSearcher;
import net.stargraph.core.index.IndexPopulator;
import net.stargraph.core.ner.NER;
import net.stargraph.core.search.*;
import net.stargraph.data.DataProvider;
import net.stargraph.data.DataProviderFactory;
import net.stargraph.data.processor.Holder;
import net.stargraph.model.KBId;
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
public final class KBCore {
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
    private Map<String, IndexSearcher> indexSearchers;
    private boolean running;

    public KBCore(String kbName, Stargraph stargraph, boolean start) {
        this.kbName = Objects.requireNonNull(kbName);
        this.stargraph = Objects.requireNonNull(stargraph);
        this.mainConfig = stargraph.getMainConfig();
        this.kbConfig = mainConfig.getConfig(String.format("kb.%s", kbName));
        this.marker = MarkerFactory.getMarker(kbName);
        this.indexPopulators = new ConcurrentHashMap<>();
        this.indexSearchers = new ConcurrentHashMap<>();
        this.language = Language.valueOf(kbConfig.getString("language").toUpperCase());
        this.namespace = Namespace.create(kbConfig);

        if (start) {
            initialize();
        }
    }

    public synchronized void initialize() {
        if (running) {
            throw new IllegalStateException("Already started.");
        }

        final List<String> modelNames = getKBIds().stream().map(KBId::getModel).collect(Collectors.toList());

        for (String modelId : modelNames) {
            logger.info(marker, "Initializing '{}'", modelId);
            final KBId kbId = KBId.of(kbName, modelId);
            IndicesFactory factory = stargraph.getIndicesFactory(kbId);

            IndexPopulator indexPopulator = factory.createIndexer(kbId, stargraph);

            if (indexPopulator != null) {
                indexPopulator.start();
                indexPopulators.put(modelId, indexPopulator);
            } else {
                logger.warn(marker, "No indexPopulator created for {}", kbId);
            }

            BaseIndexSearcher searcher = factory.createSearcher(kbId, stargraph);

            if (searcher != null) {
                searcher.start();
                indexSearchers.put(modelId, searcher);
            } else {
                logger.warn(marker, "No searcher created for {}", kbId);
            }
        }

        this.kbLoader = new KBLoader(this);
        this.running = true;
    }


    public synchronized void terminate() {
        if (!running) {
            throw new IllegalStateException("Already stopped.");
        }
        logger.info(marker, "Terminating '{}'", kbName);

        indexPopulators.values().forEach(IndexPopulator::stop);
        indexSearchers.values().forEach(IndexSearcher::stop);

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

    public List<KBId> getKBIds() {
        ConfigObject typeObj = this.mainConfig.getObject(String.format("kb.%s.model", kbName));
        return typeObj.keySet().stream().map(modelName -> KBId.of(kbName, modelName)).collect(Collectors.toList());
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
        throw new StarGraphException("IndexPopulator not found nor initialized: " + KBId.of(kbName, modelId));
    }

    public IndexSearcher getIndexSearcher(String modelId) {
        checkRunning();
        if (indexSearchers.containsKey(modelId)) {
            return indexSearchers.get(modelId);
        }
        throw new StarGraphException("Searcher not found nor initialized: " + KBId.of(kbName, modelId));
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

    public EntitySearchBuilder createEntitySearcher() {
        return createEntitySearcher("entities");
    }

    public EntitySearchBuilder createEntitySearcher(String index) {
        IndicesFactory factory = stargraph.getIndicesFactory(KBId.of(kbName, index));
        return factory.createEntitySearcher(this);
    }

    public DocumentSearchBuilder createDocumentSearcher() {
        IndicesFactory factory = stargraph.getIndicesFactory(KBId.of(kbName, "documents"));
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


        for (KBId kbId : this.getKBIds()) {
            logger.debug("For KBid {}...", kbId);
            // create <? extends Holder> from List of statements.
            IndexPopulator indexer = indexPopulators.get(kbId.getModel());
            logger.debug("Got indexer of type {}", indexer.getClass());
            // create dataProvider from given model
            DataProviderFactory dataProviderFactory = stargraph.createDataProviderFactory(kbId);
            logger.debug("Created DataProviderFactory of type {}", dataProviderFactory.getClass());
            DataProvider<? extends Holder> dataProvider = dataProviderFactory.create(kbId, data);
            logger.debug("Created DataProvider of type {}", dataProvider.getClass());
            indexer.extend(dataProvider);
        }
    }
}
