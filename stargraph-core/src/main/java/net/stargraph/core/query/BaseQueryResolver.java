package net.stargraph.core.query;

import net.stargraph.core.KBCore;
import net.stargraph.core.search.DocumentSearchBuilder;
import net.stargraph.core.search.EntitySearchBuilder;
import net.stargraph.core.search.SearchBuilder;

public abstract class BaseQueryResolver implements QueryResolver {
    private KBCore knowledgeBase;

    public BaseQueryResolver() {
        EntitySearchBuilder builder = getSearchBuilder("entities");
        DocumentSearchBuilder builder1 = getSearchBuilder("documents");


    }

    protected final <T extends SearchBuilder> T getSearchBuilder(String searchable) {
        return knowledgeBase.getSearchBuilder(searchable);
    }


}
