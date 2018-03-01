package net.stargraph.core;

import net.stargraph.core.index.IndexPopulator;
import net.stargraph.core.search.database.DatabaseResult;
import net.stargraph.core.search.executor.IndexSearchExecutor;
import net.stargraph.core.search.index.IndexSearcher;

public final class Index {
    private IndexSearcher indexSearcher;
    private IndexSearchExecutor indexSearchExecutor;
    private IndexPopulator indexPopulator;

    public IndexSearcher getSearcher() {
        return indexSearcher;
    }

}
