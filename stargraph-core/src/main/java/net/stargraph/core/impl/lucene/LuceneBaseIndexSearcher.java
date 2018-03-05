package net.stargraph.core.impl.lucene;

import net.stargraph.core.Index;
import net.stargraph.core.impl.elastic.ElasticIndexSearchExecutor;
import net.stargraph.core.search.index.BaseIndexSearcher;
import net.stargraph.core.search.index.IndexSearcher;
import org.apache.lucene.search.Query;
import org.elasticsearch.index.query.QueryBuilder;

import java.io.Serializable;
import java.util.Objects;

/**
 * Class to rid the actual implementing Lucene Index Searchers of some of the type parameters they have in common.
 *
 * @param <R> Type of the model which the index contains.
 */
class LuceneBaseIndexSearcher<R extends Serializable>
        extends BaseIndexSearcher<LuceneIndexSearchExecutor<R>, R, Query> {
    LuceneBaseIndexSearcher(Index index) {
        super(index);
    }
}
