package net.stargraph.core.impl.elastic;

import net.stargraph.core.Index;
import net.stargraph.core.search.index.BaseIndexSearcher;
import org.elasticsearch.index.query.QueryBuilder;

import java.io.Serializable;

/**
 * Class to rid the actual implementing Elastic Index Searchers of some of the type parameters they have in common.
 *
 * @param <R> Type of the model which the index contains.
 */
public abstract class ElasticBaseIndexSearcher<R extends Serializable>
        extends BaseIndexSearcher<ElasticIndexSearchExecutor<R>, R, QueryBuilder> {

    ElasticBaseIndexSearcher(Index index) {
        super(index);
    }
}
