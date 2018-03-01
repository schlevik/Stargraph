package net.stargraph.core.search.database;

/**
 * @param <Q> Type of the Query.
 * @param <R> Type of the Result.
 */
public interface Database<Q extends DatabaseQuery, R extends DatabaseResult> {
    R query(Q query);

    DBType getType();
}
