package net.stargraph.core.query;

import net.stargraph.core.KnowledgeBase;
import net.stargraph.core.search.index.DocumentIndexSearcher;
import net.stargraph.core.search.index.EntityIndexSearcher;
import net.stargraph.core.search.index.IndexSearcher;
import net.stargraph.model.IndexID;

public abstract class BaseQueryResolver implements QueryResolver {
    private KnowledgeBase knowledgeBase;

    public BaseQueryResolver() {

    }

    protected final <T extends IndexSearcher> T getIndexSearcher(IndexID indexID) {
        return (T) knowledgeBase.getIndex(indexID.getIndex()).getSearcher();
    }


}
