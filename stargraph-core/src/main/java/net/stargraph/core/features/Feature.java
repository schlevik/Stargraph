package net.stargraph.core.features;

import net.stargraph.core.KnowledgeBase;

public interface Feature<T> {
    T get(KnowledgeBase knowledgeBase);

}
