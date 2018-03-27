package net.stargraph.core;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import net.stargraph.StarGraphException;
import net.stargraph.StargraphConfigurationException;
import net.stargraph.core.features.Feature;
import net.stargraph.core.features.NERFeature;
import net.stargraph.core.search.database.*;
import net.stargraph.core.index.IndexPopulator;
import net.stargraph.core.search.executor.IndexSearchExecutor;
import net.stargraph.core.search.index.IndexSearcher;
import net.stargraph.model.IndexID;
import net.stargraph.query.Language;
import net.stargraph.rank.ModifiableIndraParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A compound class representing a knowledge base in the stargraph universe.
 * <p>
 * Consists of a database and a number of indices. Knowledge bases can be isComposed in which case the knowledge base
 * contains a reference to the knowledge base it enriches. Additionally knowledge bases can be a list of structurally
 * identical different knowledge bases, in which case the knowledge base contains a list of references to the knowledge
 * bases it is listed of.
 */
public final class KnowledgeBase {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker;

    private String name;
    private Language language;
    private KBLoader kbLoader;
    private Namespace namespace;
    private Stargraph stargraph;
    private Set<Feature> features;
    private boolean running;
    private boolean isComposed;
    private KnowledgeBase composedOf;

    //
    private Map<String, Index> indices;
    private Database database;

    public KnowledgeBase(String name, Stargraph stargraph, boolean start) {
        this.name = Objects.requireNonNull(name);
        this.stargraph = Objects.requireNonNull(stargraph);

        this.marker = MarkerFactory.getMarker(name);

        String lang = stargraph.getConfig().language(name);
        if (lang == null) {
            lang = stargraph.getConfig().defaultLanguage();
        }
        this.language = Language.valueOf(lang.toUpperCase());

        this.namespace = Namespace.create(stargraph.getConfig().getKBConfig(name));

        // TODO: this should be red from config at some point
        this.features = Collections.singleton(new NERFeature());

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
        // create list of index names, put them into the map, initialize indices.
        getIndexIDs().forEach(id -> {
            Index index = new Index(id, stargraph);
            indices.put(id.getIndex(), index);
            index.initialize();
        });


        DatabaseFactory factory = stargraph.createDatabaseFactoryForKB(this.name);
        this.database = factory.getDatabase(this);

        // determine if kb is composed
        // if so, set appropriately
        String composedName = stargraph.getConfig().composedOf(this.name);
        if (composedName != null) {
            if (!stargraph.hasKB(composedName)) {
                throw new StargraphConfigurationException("KB '{}' is composed on top of '{}', which is undefined/not initialized/disabled!");
            }
            this.isComposed = true;
            this.composedOf = stargraph.getKnowledgeBase(composedName);
        }

        this.kbLoader = new KBLoader(this);
        this.running = true;
    }

    @SuppressWarnings("unchecked")
    public <F extends Feature<T>, T> T getFeature(Class<F> featureCls) {
        for (Feature feature : features) {
            if (feature.getClass().equals(featureCls)) return ((F) feature).get(this);
        }
        if (isComposed) {
            return composedOf.getFeature(featureCls);
        }
        throw new StargraphConfigurationException(String.format("KB %s does not provide feature %s!", this.name, featureCls.getName()));
    }

    public Index getIndex(String indexName) {
        Index index = this.indices.get(indexName);
        if (index == null) {
            if (isComposed) {
                return composedOf.getIndex(indexName);
            } else
                throw new StargraphConfigurationException(String.format(
                        "The index %s is not configured for Knowledge base %s.",
                        name,
                        indexName
                ));
        } else {
            return index;
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends IndexSearcher> T getSearcher(String indexName) {
        return (T) getIndex(indexName).getSearcher();
    }

    @SuppressWarnings("unchecked")
    public <T extends IndexSearcher> T getSearcher(Class<T> searcherIface) {
        for (Index index : indices.values()) {
            if (searcherIface.isAssignableFrom(index.getSearcher().getClass())) {
                return (T) index.getSearcher();
            }
        }
        if (isComposed) {
            return composedOf.getSearcher(searcherIface);
        }
        throw new StargraphConfigurationException("No index provides a " + searcherIface.getName() + " searcher!");
    }

    public DatabaseResult queryDatabase(DatabaseQuery query) {
        Database db = this.getDatabase(query.getType());
        return db.query(query);

    }

    public Database getDatabase(DBType type) {
        checkRunning();
        if (this.database.getType() == type) {
            return this.database;
        } else {
            if (isComposed) {
                return composedOf.getDatabase(type);
            } else {
                throw new StargraphConfigurationException(String.format(
                        "No database with type %s is configured for knowledge base %s.",
                        type,
                        name));
            }
        }
    }

    public synchronized void terminate() {
        if (!running) {
            throw new IllegalStateException("Already stopped.");
        }
        logger.info(marker, "Terminating '{}'", name);

        this.indices.values().forEach(Index::terminate);

        this.running = false;
    }


    public String getName() {
        return name;
    }

    public Language getLanguage() {
        return language;
    }

    public List<IndexID> getIndexIDs() {
        return stargraph.getConfig().indices(name).stream()
                .map(index -> IndexID.of(name, index))
                .collect(Collectors.toList());
    }


    public IndexPopulator getIndexPopulator(String indexName) {
        checkRunning();
        if (indices.containsKey(indexName)) {
            return indices.get(indexName).getPopulator();
        }

        throw new StarGraphException("Populator either not found or not initialized: " + IndexID.of(name, indexName));
    }

    public IndexSearchExecutor getSearchExecutor(String indexName) {
        checkRunning();
        if (indices.containsKey(indexName)) {
            return indices.get(indexName).getSearchExecutor();
        }
        throw new StarGraphException("Searcher either not found or not initialized: " + IndexID.of(name, indexName));
    }

    public KBLoader getLoader() {
        checkRunning();
        return kbLoader;
    }


    public Namespace getNamespace() {
        return namespace;
    }


    public void configureDistributionalParams(ModifiableIndraParams params) {
        String indraUrl = stargraph.getConfig().distServiceUrl();
        String indraCorpus = stargraph.getConfig().distServiceCorpus();
        params.url(indraUrl).corpus(indraCorpus).language(language.code);
    }

    private void checkRunning() {
        if (!running) {
            throw new IllegalStateException("KB Core not started.");
        }
    }

    @Override
    public String toString() {
        return "KnowledgeBase{" +
                "\nname=" + name +
                "\nindices=" + indices.keySet() +
                "\nlanguage=" + language +
                "\nnamespace=" + namespace +
                "\nloader=" + (kbLoader == null ? "none" : "initialized") +
                "\n}";
    }

//    public void extend(List<Statement> data) {
//        // TODO: 1/5/18 This is bound to JENA/RDF atm
//        /*
//         * Main idea:
//         * 1) add all statements to graph V
//         * 2) get all indexPopulators for this KB
//         * 3) for each indexer, index newly incoming data
//         * 4) uuuuh yea, sth like that
//         */
//        logger.trace("Entering extend");
//        Model model = null;
//        logger.debug("Adding incoming data to graph.");
//        model.add(data);
//
//
//        for (IndexID indexID : this.getIndexIDs()) {
//            logger.debug("For KBid {}...", indexID);
//            // create <? extends Holder> from List of statements.
//            IndexPopulator indexer = getIndexPopulator(indexID.getIndex());
//            logger.debug("Got indexer of type {}", indexer.getClass());
//            // create dataProvider from given index
//            DataProviderFactory dataProviderFactory = stargraph.createDataProviderFactory(indexID);
//            logger.debug("Created DataProviderFactory of type {}", dataProviderFactory.getClass());
//            DataProvider<? extends Holder> dataProvider = dataProviderFactory.create(indexID, data);
//            logger.debug("Created DataProvider of type {}", dataProvider.getClass());
//            indexer.extend(dataProvider);
//        }
//    }
}
