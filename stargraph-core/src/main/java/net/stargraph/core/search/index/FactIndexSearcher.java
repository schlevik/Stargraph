package net.stargraph.core.search.index;

import net.stargraph.core.search.executor.IndexSearchExecutor;
import net.stargraph.model.Fact;
import net.stargraph.model.InstanceEntity;
import net.stargraph.model.LabeledEntity;
import net.stargraph.model.PropertyEntity;
import net.stargraph.rank.ModifiableRankParams;
import net.stargraph.rank.ModifiableSearchParams;
import net.stargraph.rank.Scores;

public interface FactIndexSearcher extends IndexSearcher {

    Scores<PropertyEntity> pivotedSearch(InstanceEntity pivot, ModifiableSearchParams searchParams, ModifiableRankParams rankParams);

    Scores<LabeledEntity> classSearch(ModifiableSearchParams searchParams, ModifiableRankParams rankParams);


}
