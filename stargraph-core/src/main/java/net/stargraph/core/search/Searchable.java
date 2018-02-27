package net.stargraph.core.search;

/**
 * Basic searchable unit in the stargraph universe.
 * <p>
 * Can be a database, can be an index.
 *
 * @param <T> Type of the result returned by the search query.
 * @param <Q> Type of the query.
 */
public interface Searchable<T, Q> {
    T search(Q query);
}
