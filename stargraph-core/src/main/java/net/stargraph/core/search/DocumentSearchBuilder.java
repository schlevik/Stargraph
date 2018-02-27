package net.stargraph.core.search;

import net.stargraph.model.InstanceEntity;
import net.stargraph.rank.ModifiableRankParams;
import net.stargraph.rank.ModifiableSearchParams;
import net.stargraph.rank.Scores;

public interface DocumentSearchBuilder<T extends IndexSearcher> extends GenericSearchBuilder<T> {
    Scores pivotedFullTextPassageSearch(InstanceEntity pivot,
                                        ModifiableSearchParams searchParams, ModifiableRankParams rankParams);


}
