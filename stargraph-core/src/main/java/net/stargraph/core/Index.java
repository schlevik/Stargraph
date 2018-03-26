package net.stargraph.core;

import com.typesafe.config.Config;
import net.stargraph.core.index.IndexPopulator;
import net.stargraph.core.search.executor.IndexSearchExecutor;
import net.stargraph.core.search.index.IndexSearcher;
import net.stargraph.model.IndexID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.Objects;

public final class Index {
    private IndexSearcher indexSearcher;
    private IndexSearchExecutor indexSearchExecutor;
    private IndexPopulator indexPopulator;
    private IndexID indexID;
    private Stargraph stargraph;

    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker;

    public Index(IndexID id, Stargraph stargraph) {
        this.indexID = Objects.requireNonNull(id);
        this.stargraph = Objects.requireNonNull(stargraph);
        this.marker = MarkerFactory.getMarker(indexID.toString());
    }

    public IndexSearcher getSearcher() {
        return indexSearcher;
    }


    public IndexID getID() {
        return indexID;
    }

    void initialize() {
        logger.info(marker, "Initializing '{}'", indexID);

        IndexFactory indexFactory = stargraph.createIndexFactoryForID(indexID);

        indexPopulator = indexFactory.createIndexer(indexID, stargraph);

        if (indexPopulator != null) {
            indexPopulator.start();
        } else {
            logger.warn(marker, "No indexPopulator created for {}", indexID);
        }

        this.indexSearchExecutor = indexFactory.createSearchExecutor(indexID, stargraph);


        if (indexSearchExecutor != null) {
            indexSearchExecutor.start();

        } else {
            logger.warn(marker, "No Search Executor created for {}", indexID);
        }

        this.indexSearcher = indexFactory.createIndexSearcher(this, stargraph);
    }

    void terminate() {
        indexPopulator.stop();
        indexSearchExecutor.stop();
    }

    public IndexPopulator getPopulator() {
        return this.indexPopulator;
    }

    public IndexSearchExecutor getSearchExecutor() {
        return this.indexSearchExecutor;
    }

    public KnowledgeBase getKnowledgeBase() {
        return stargraph.getKnowledgeBase(indexID.getKnowledgeBase());
    }

    @Override
    public String toString() {
        return "Index{" +
                "\nid=" + indexID +
                "\nExecutor=" + indexSearchExecutor.getClass().getName() +
                "\nPopulator=" + indexPopulator.getClass().getName() +
                "\nSearcher=" + indexSearcher.getClass().getName() +
                "\n}";
    }
}
