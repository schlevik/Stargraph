package net.stargraph.core.impl.elastic;

import net.stargraph.core.Index;
import net.stargraph.core.search.index.FactIndexSearcher;
import net.stargraph.model.*;
import net.stargraph.rank.*;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.QueryBuilder;

import java.util.List;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.*;

public class ElasticFactIndexSearcher extends ElasticBaseIndexSearcher<Fact> implements FactIndexSearcher {

    public ElasticFactIndexSearcher(Index index) {
        super(index);
    }


    @Override
    public Scores<PropertyEntity> pivotedSearch(InstanceEntity pivot, ModifiableSearchParams searchParams, ModifiableRankParams rankParams) {

        searchParams.index(getIndex().getID());

        configureDistParams(rankParams);

        QueryBuilder queryBuilder = boolQuery()
                .should(nestedQuery("s", termQuery("s.id", pivot.getId()), ScoreMode.Max))
                .should(nestedQuery("o", termQuery("o.id", pivot.getId()), ScoreMode.Max)).minimumShouldMatch(1);
//        return null;
        Scores<Fact> scores = executeSearch(queryBuilder, searchParams);

        // We have to remap the facts to properties, the real target of the ranker call.
        // Thus we're discarding the score values from the underlying search engine. Shall we?
        Scores<PropertyEntity> propScores = scores.stream()
                .map(s -> (s.getEntry()).getPredicate())
                .distinct()
                .map(p -> new Score<>(p, 0)).collect(Collectors.toCollection(Scores::new));

        return Rankers.apply(propScores, rankParams, searchParams.getSearchTerm());

    }

    @Override
    public Scores<LabeledEntity> classSearch(ModifiableSearchParams searchParams, ModifiableRankParams rankParams) {

        searchParams.index(getIndex().getID());

        configureDistParams(rankParams);

        QueryBuilder queryBuilder = boolQuery()
                .must(nestedQuery("p",
                        termQuery("p.id", "is-a"), ScoreMode.Max))
                .should(nestedQuery("o",
                        matchQuery("o.value", searchParams.getSearchTerm()), ScoreMode.Max))
                .minimumShouldMatch("1");
//        return null;
        Scores<Fact> scores = executeSearch(queryBuilder, searchParams);

        List<Score<LabeledEntity>> classes2Score = scores.stream()
                .map(s -> new Score<>((s.getEntry()).getObject(), s.getValue())).collect(Collectors.toList());

        return Rankers.apply(new Scores<>(classes2Score), rankParams, searchParams.getSearchTerm());
    }
}
