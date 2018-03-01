package net.stargraph.core.impl.lucene;

import net.stargraph.core.KnowledgeBase;
import net.stargraph.core.search.index.EntityIndexSearcher;
import net.stargraph.model.BuiltInModel;
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
import java.util.Objects;

public class LuceneEntityIndexSearcher implements EntityIndexSearcher<LuceneIndexSearchExecutor> {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker = MarkerFactory.getMarker("lucene");

    private KnowledgeBase core;


    public LuceneEntityIndexSearcher(KnowledgeBase core) {
        this.core = Objects.requireNonNull(core);
    }

    @Override
    public LabeledEntity getEntity(String dbId, String id) {
        throw new NotImplementedException();
    }

    @Override
    public List<LabeledEntity> getEntities(String dbId, List<String> ids) {
        throw new NotImplementedException();
    }

    @Override
    public Scores classSearch(ModifiableSearchParams searchParams, ModifiableRankParams rankParams) {
        throw new NotImplementedException();
    }

    @Override
    public Scores instanceSearch(ModifiableSearchParams searchParams, ModifiableRankParams rankParams) {
        //That's what we're trying to do here.
        QueryBuilder queryBuilder = new QueryBuilder(new StandardAnalyzer());
        Query query = queryBuilder.createPhraseQuery("value", searchParams.getSearchTerm(), 0);
        searchParams.model(BuiltInModel.ENTITY);
        LuceneIndexSearchExecutor searcher = getSearchExecutor(core, searchParams.getKbId().getIndex());
        Scores scores = searcher.search(new LuceneQueryHolder(query, searchParams));
        return Rankers.apply(scores, rankParams, searchParams.getSearchTerm());
    }

    @Override
    public Scores propertySearch(ModifiableSearchParams searchParams, ModifiableRankParams rankParams) {
        throw new NotImplementedException();
    }

    @Override
    public Scores pivotedSearch(InstanceEntity pivot, ModifiableSearchParams searchParams, ModifiableRankParams rankParams) {
        throw new NotImplementedException();
    }

}
