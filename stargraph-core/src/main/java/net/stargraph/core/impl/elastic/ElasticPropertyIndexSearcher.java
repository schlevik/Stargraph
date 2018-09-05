package net.stargraph.core.impl.elastic;

import net.stargraph.core.Index;
import net.stargraph.core.search.index.PropertyIndexSearcher;
import net.stargraph.model.BuiltInIndex;
import net.stargraph.model.PropertyEntity;
import net.stargraph.rank.ModifiableRankParams;
import net.stargraph.rank.ModifiableSearchParams;
import net.stargraph.rank.Rankers;
import net.stargraph.rank.Scores;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.QueryBuilder;


import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;

public class ElasticPropertyIndexSearcher extends ElasticBaseIndexSearcher<PropertyEntity> implements PropertyIndexSearcher {


    public ElasticPropertyIndexSearcher(Index index) {
        super(index);
    }

    @Override
    public Scores<PropertyEntity> propertySearch(ModifiableSearchParams searchParams, ModifiableRankParams rankParams) {

        searchParams.index(BuiltInIndex.PROPERTY);

        configureDistParams(rankParams);

        org.elasticsearch.index.query.QueryBuilder queryBuilder = boolQuery()
                .should(nestedQuery("hyponyms",
                        matchQuery("hyponyms.word", searchParams.getSearchTerm()), ScoreMode.Max))
                .should(nestedQuery("hypernyms",
                        matchQuery("hypernyms.word", searchParams.getSearchTerm()), ScoreMode.Max))
                .should(nestedQuery("synonyms",
                        matchQuery("synonyms.word", searchParams.getSearchTerm()), ScoreMode.Max))
                .minimumShouldMatch(1);
        Scores<PropertyEntity> scores = this.executeSearch(queryBuilder, searchParams);

        return Rankers.apply(scores, rankParams, searchParams.getSearchTerm());
    }

}
