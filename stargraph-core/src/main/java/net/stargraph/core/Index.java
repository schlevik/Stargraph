package net.stargraph.core;

import net.stargraph.core.search.SearchBuilder;

public final class Index<T extends SearchBuilder> {
    private T indexSearchBuilder;

    public T getSearchBuilder() {
        return indexSearchBuilder;
    }
}
