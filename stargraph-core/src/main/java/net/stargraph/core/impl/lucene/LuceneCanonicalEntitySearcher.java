package net.stargraph.core.impl.lucene;

import net.stargraph.ModelUtils;
import net.stargraph.core.KBCore;
import net.stargraph.core.search.EntitySearcher;
import net.stargraph.core.search.Searcher;
import net.stargraph.model.BuiltInModel;
import net.stargraph.model.CanonicalInstanceEntity;
import net.stargraph.model.InstanceEntity;
import net.stargraph.model.LabeledEntity;
import net.stargraph.rank.*;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.QueryBuilder;
import org.elasticsearch.index.query.FuzzyQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LuceneCanonicalEntitySearcher implements EntitySearcher<LuceneSearcher> {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker = MarkerFactory.getMarker("lucene");

    private KBCore core;


    public LuceneCanonicalEntitySearcher(KBCore core) {
        this.core = Objects.requireNonNull(core);
        logger.info(marker, "I WAS CREATED!!!");
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
        LuceneSearcher searcher = this.getSearcher(core, searchParams.getKbId().getModel());

        Scores scores = searcher.search(new LuceneQueryHolder(query, searchParams));
        scores = replaceCanonical(scores);
        return Rankers.apply(scores, rankParams, searchParams.getSearchTerm());
    }

    private Score replaceWithRef(Score score) {
        CanonicalInstanceEntity entity = (CanonicalInstanceEntity) score.getEntry();
        String reference = entity.getReference();
        if (!reference.equals("")) {
            logger.trace(marker, "Replacing {} with ref {}", score, reference);
            return new Score(ModelUtils.createInstance(reference), score.getValue());
        }
        return new Score(ModelUtils.createInstance(entity.getId()), score.getValue());
    }


    private Scores replaceCanonical(Scores scores) {
        return new Scores(scores.stream()
                .map(this::replaceWithRef)
                .distinct()
                .collect(Collectors.toList()));
        // this instead of distinct
        //.filter(score -> {
        //                    String id = ((LabeledEntity) score.getEntry()).getId();
        //                    return ids.add(id);
        //                }).
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
