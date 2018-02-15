package net.stargraph.core.impl.lucene;

import net.stargraph.core.search.SearchQueryHolder;
import net.stargraph.rank.ModifiableSearchParams;
import org.apache.lucene.search.Query;

import java.util.Objects;

public class LuceneQueryHolder implements SearchQueryHolder<Query> {

    @Override
    public ModifiableSearchParams getSearchParams() {
        return searchParams;
    }

    public Query getQuery() {
        return query;
    }

    private ModifiableSearchParams searchParams;
    private Query query;

    public LuceneQueryHolder(Query query, ModifiableSearchParams searchParams) {
        this.query = Objects.requireNonNull(query);
        this.searchParams = Objects.requireNonNull(searchParams);
    }

}
