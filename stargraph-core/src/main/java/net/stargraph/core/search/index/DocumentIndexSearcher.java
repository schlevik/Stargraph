package net.stargraph.core.search.index;

import net.stargraph.core.search.executor.IndexSearchExecutor;
import net.stargraph.model.InstanceEntity;
import net.stargraph.rank.ModifiableRankParams;
import net.stargraph.rank.ModifiableSearchParams;
import net.stargraph.rank.Scores;

public interface DocumentIndexSearcher<T extends IndexSearchExecutor> extends IndexSearcher<T> {
    Scores pivotedFullTextPassageSearch(InstanceEntity pivot,
                                        ModifiableSearchParams searchParams, ModifiableRankParams rankParams);


}
