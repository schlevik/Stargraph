package net.stargraph.core.query;


public interface QueryResolver {

    /**
     * This method defines the act of resolving the given query.
     * <p>
     * Should append its answer if it can provide one after parsing the input, and not do anything if it cannot.
     *
     * @param query         Query to resolve.
     * @param knowledgeBase Knowledge base to query.
     */
    void resolveQuery(Query query, String knowledgeBase);


}