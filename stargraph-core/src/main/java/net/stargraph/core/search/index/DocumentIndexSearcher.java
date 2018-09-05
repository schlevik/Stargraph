package net.stargraph.core.search.index;

import net.stargraph.model.InstanceEntity;
import net.stargraph.model.Passage;
import net.stargraph.rank.ModifiableRankParams;
import net.stargraph.rank.ModifiableSearchParams;
import net.stargraph.rank.Scores;

public interface DocumentIndexSearcher extends IndexSearcher {
    Scores<Passage> pivotedFullTextPassageSearch(InstanceEntity pivot,
                                                 ModifiableSearchParams searchParams, ModifiableRankParams rankParams);


}
