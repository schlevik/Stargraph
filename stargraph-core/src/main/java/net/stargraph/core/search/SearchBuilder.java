package net.stargraph.core.search;

import net.stargraph.StarGraphException;
import net.stargraph.core.KBCore;

public interface SearchBuilder<T extends IndexSearcher> {
    default T getBackendSearcher(KBCore core, String index) {
        T searcher;
        try {
            searcher = (T) core.getIndexSearcher(index);
        } catch (ClassCastException e) {
            try {
                throw new StarGraphException(String.format(
                        "Tried to cast to %s but class was %s! Get your generics straight!",
                        this.getClass().getMethod("getBackendSearcher", KBCore.class, String.class).getReturnType(),
                        core.getIndexSearcher(index))
                );
            } catch (Exception e1) {
                throw new RuntimeException();
            }
        }

        return searcher;
    }
}
