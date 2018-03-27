package net.stargraph.core.impl.lucene;

import net.stargraph.core.Index;
import net.stargraph.core.impl.corenlp.NERSearcher;
import net.stargraph.core.ner.NER;
import net.stargraph.core.search.index.EntityIndexSearcher;
import net.stargraph.model.InstanceEntity;
import net.stargraph.model.LabeledEntity;
import net.stargraph.rank.ModifiableRankParams;
import net.stargraph.rank.ModifiableSearchParams;
import net.stargraph.rank.Rankers;
import net.stargraph.rank.Scores;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.List;

public class LuceneEntityIndexSearcher extends LuceneBaseIndexSearcher<InstanceEntity>
        implements EntityIndexSearcher {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker = MarkerFactory.getMarker("lucene");

    public LuceneEntityIndexSearcher(Index index) {
        super(index);
    }


    @Override
    public LabeledEntity getEntity(String id) {
        throw new NotImplementedException();
    }

    @Override
    public List<LabeledEntity> getEntities(List<String> ids) {
        throw new NotImplementedException();
    }

    @Override
    public Scores<InstanceEntity> instanceSearch(ModifiableSearchParams searchParams, ModifiableRankParams rankParams) {
        //That's what we're trying to do here.
        searchParams.index(getIndex().getID());

        Query query = new QueryBuilder(new StandardAnalyzer())
                .createPhraseQuery("value", searchParams.getSearchTerm(), 0);

        Scores<InstanceEntity> scores = executeSearch(query, searchParams);

        return Rankers.apply(scores, rankParams, searchParams.getSearchTerm());
    }


}
