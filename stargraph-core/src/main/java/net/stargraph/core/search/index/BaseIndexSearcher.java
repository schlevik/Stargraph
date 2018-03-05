package net.stargraph.core.search.index;

import net.stargraph.StarGraphException;
import net.stargraph.core.Index;
import net.stargraph.core.impl.corenlp.NERSearcher;
import net.stargraph.core.ner.NER;
import net.stargraph.core.search.executor.IndexSearchExecutor;
import net.stargraph.rank.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * This class basically provides a type-safe {@link BaseIndexSearcher#executeSearch(Q, ModifiableSearchParams)} method
 * for every implementing index searcher (provided they got their generics right).
 * <p>
 * Implementing classes should implement an abstract getIndex method.
 *
 * @param <E> Type of the {@link IndexSearchExecutor}, e.g.
 *            {@link net.stargraph.core.impl.elastic.ElasticIndexSearchExecutor}.
 * @param <R> Type of the result the searcher is expected to return, e.g. {@link net.stargraph.model.ClassEntity},
 *            must extend {@link Serializable}.
 * @param <Q> Type of the query the {@link IndexSearchExecutor} is expected to take as input to perform the query.
 */
public abstract class BaseIndexSearcher<E extends IndexSearchExecutor<R, Q>, R extends Serializable, Q> {
    private Index index;
    private E searchExecutor;

    @SuppressWarnings("unchecked")
    protected BaseIndexSearcher(Index index) {
        this.index = Objects.requireNonNull(index);
        try {
            this.searchExecutor = (E) index.getSearchExecutor();
        } catch (ClassCastException e) {
            throw new StarGraphException("Implementation error! Get your generics straight!");
        }
    }


    public Index getIndex() {
        return index;
    }

    protected final void configureDistParams(ModifiableRankParams params) {
        if (params instanceof ModifiableIndraParams) {
            index.getKnowledgeBase().configureDistributionalParams((ModifiableIndraParams) params);
        }
    }

    /**
     * Type-safe function to execute an actual search on the implementing backend.
     *
     * @param query  Query to perform.
     * @param params Search parameters.
     * @return Result of the query.
     */
    protected final Scores<R> executeSearch(Q query, ModifiableSearchParams params) {
        return searchExecutor.search(query, params);
    }


}
