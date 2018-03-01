package net.stargraph.core.search.index;

import net.stargraph.StarGraphException;
import net.stargraph.core.KnowledgeBase;
import net.stargraph.core.search.executor.IndexSearchExecutor;

public interface IndexSearcher<T extends IndexSearchExecutor> {
    default T getSearchExecutor(KnowledgeBase core, String index) {
        T searcher;
        try {
            searcher = (T) core.getSearchExecutor(index);
        } catch (ClassCastException e) {
            try {
                throw new StarGraphException(String.format(
                        "Tried to cast to %s but class was %s! Get your generics straight!",
                        this.getClass().getMethod("getSearchExecutor", KnowledgeBase.class, String.class).getReturnType(),
                        core.getSearchExecutor(index))
                );
            } catch (Exception e1) {
                throw new RuntimeException();
            }
        }

        return searcher;
    }
}
