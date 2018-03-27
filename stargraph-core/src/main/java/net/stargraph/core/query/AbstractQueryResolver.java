package net.stargraph.core.query;

import net.stargraph.core.KnowledgeBase;
import net.stargraph.core.search.index.IndexSearcher;
import net.stargraph.model.IndexID;

public abstract class AbstractQueryResolver {
    private KnowledgeBase knowledgeBase;
    private Analyzers analyzers;

    public AbstractQueryResolver(KnowledgeBase knowledgeBase, Analyzers analyzers) {
        this.knowledgeBase = knowledgeBase;
    }

    protected final Analyzers getAnalyzers() {
        return analyzers;
    }
    protected final KnowledgeBase getKnowledgeBase() {
        return knowledgeBase;
    }

    /**
     * This method defines the act of resolving the given query.
     * <p>
     * Should append its answer if it can provide one after parsing the input, and not do anything if it cannot.
     *
     * @param query Query to resolve.
     */
    public abstract void resolveQuery(Query query);

//    protected KnowledgeBase getKnowledgeBase() {
//        return this.knowledgeBase;
//    }

    protected final <T extends IndexSearcher> T getIndexSearcher(IndexID indexID) {
        return (T) knowledgeBase.getIndex(indexID.getIndex()).getSearcher();
    }

    protected final <T extends IndexSearcher> T getIndexSearcher(Class<T> cls) {
        //TODO: at some point, there should be a mapping red from the config and calling the function above
        return knowledgeBase.getSearcher(cls);
    }


}
