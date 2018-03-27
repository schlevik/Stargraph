package net.stargraph.core.impl.lucene;

import com.sun.org.apache.bcel.internal.generic.INSTANCEOF;
import net.stargraph.ModelUtils;
import net.stargraph.core.Index;
import net.stargraph.core.KnowledgeBase;
import net.stargraph.core.impl.corenlp.NERSearcher;
import net.stargraph.core.ner.NER;
import net.stargraph.core.search.index.EntityIndexSearcher;
import net.stargraph.model.BuiltInIndex;
import net.stargraph.model.CanonicalInstanceEntity;
import net.stargraph.model.InstanceEntity;
import net.stargraph.model.LabeledEntity;
import net.stargraph.rank.*;
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
import java.util.stream.Collectors;

public class LuceneCanonicalEntityIndexSearcher extends LuceneBaseIndexSearcher<CanonicalInstanceEntity>
        implements EntityIndexSearcher {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker = MarkerFactory.getMarker("lucene");


    public LuceneCanonicalEntityIndexSearcher(Index index) {
        super(index);
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
    public Scores<InstanceEntity> instanceSearch(ModifiableSearchParams searchParams, ModifiableRankParams rankParams) {
        //That's what we're trying to do here.

        QueryBuilder queryBuilder = new QueryBuilder(new StandardAnalyzer());
        Query query = queryBuilder.createPhraseQuery("value", searchParams.getSearchTerm(), 0);
        searchParams.index(getIndex().getID());

        Scores<CanonicalInstanceEntity> scores = executeSearch(query, searchParams);
        Scores<InstanceEntity> replacedScores = replaceCanonical(scores);
        return Rankers.apply(replacedScores, rankParams, searchParams.getSearchTerm());
    }


    private Score<InstanceEntity> replaceWithRef(Score<CanonicalInstanceEntity> score) {
        CanonicalInstanceEntity entity = score.getEntry();
        String reference = entity.getReference();
        if (!reference.equals("")) {
            logger.trace(marker, "Replacing {} with ref {}", score, reference);
            return new Score<>(ModelUtils.createInstance(reference), score.getValue());
        }
        return new Score<>(ModelUtils.createInstance(entity.getId()), score.getValue());
    }


    private Scores<InstanceEntity> replaceCanonical(Scores<CanonicalInstanceEntity> scores) {
        return scores.stream()
                .map(this::replaceWithRef)
                .distinct().collect(Collectors.toCollection(Scores::new));
        // this instead of distinct
        //.filter(score -> {
        //                    String id = ((LabeledEntity) score.getEntry()).getKnowledgeBase();
        //                    return ids.add(id);
        //                }).
    }


}
