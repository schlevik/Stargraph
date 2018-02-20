package net.stargraph.core.search;

import net.stargraph.StarGraphException;
import net.stargraph.core.KBCore;

public interface GenericIndexSearcher<T extends Searcher> {
    default T getSearcher(KBCore core, String index) {
        T searcher;
        try {
            searcher = (T) core.getSearcher(index);
        } catch (ClassCastException e) {
            try {
                throw new StarGraphException(String.format(
                        "Tried to cast to %s but class was %s! Get your generics straight!",
                        this.getClass().getMethod("getSearcher", KBCore.class, String.class).getReturnType(),
                        core.getSearcher(index))
                );
            } catch (Exception e1) {
                throw new RuntimeException();
            }
        }

        return searcher;
    }
}
