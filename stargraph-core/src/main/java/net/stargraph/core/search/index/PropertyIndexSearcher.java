package net.stargraph.core.search.index;

import net.stargraph.core.search.executor.IndexSearchExecutor;
import net.stargraph.model.PropertyEntity;
import net.stargraph.rank.ModifiableRankParams;
import net.stargraph.rank.ModifiableSearchParams;
import net.stargraph.rank.Scores;

public interface PropertyIndexSearcher extends IndexSearcher {

    Scores<PropertyEntity> propertySearch(ModifiableSearchParams searchParams, ModifiableRankParams rankParams);

}
