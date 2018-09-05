package net.stargraph.core.search.database;

/**
 * @param <T> Actual type of the query (such as SPARQLQuery or String).
 */
public interface DatabaseQuery<T> {
    DBType getType();

    T getQuery();
}
