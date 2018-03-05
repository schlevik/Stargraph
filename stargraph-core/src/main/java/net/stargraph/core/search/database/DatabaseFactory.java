package net.stargraph.core.search.database;

import net.stargraph.core.KnowledgeBase;

public interface DatabaseFactory {
    Database getDatabase(KnowledgeBase knowledgeBase);
}
