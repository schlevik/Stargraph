package net.stargraph.core.impl.elastic;

import net.stargraph.core.Index;
import net.stargraph.core.search.index.DocumentIndexSearcher;
import net.stargraph.model.BuiltInIndex;
import net.stargraph.model.Document;
import net.stargraph.model.InstanceEntity;
import net.stargraph.model.Passage;
import net.stargraph.rank.ModifiableRankParams;
import net.stargraph.rank.ModifiableSearchParams;
import net.stargraph.rank.Scores;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.InnerHitBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.Objects;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;

public class ElasticDocumentIndexSearcher extends ElasticBaseIndexSearcher<Passage>
        implements DocumentIndexSearcher {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker = MarkerFactory.getMarker("elastic");

    public ElasticDocumentIndexSearcher(Index index) {
        super(index);
    }


    public Scores<Passage> pivotedFullTextPassageSearch(InstanceEntity pivot,
                                                        ModifiableSearchParams searchParams, ModifiableRankParams rankParams) {
        searchParams.index(getIndex().getID());

        String term = searchParams.getSearchTerm();
        logger.debug(marker, "performing pivoted full text passage search with pivot={} and text={}", pivot, term);
        QueryBuilder queryBuilder = nestedQuery(
                "passages",
                boolQuery()
                        .must(matchQuery("passages.text", term).operator(Operator.AND))
                        .must(nestedQuery("passages.entities",
                                matchQuery("passages.entities.id", pivot.getId()), ScoreMode.Max)),
                ScoreMode.Max)
                .innerHit(new InnerHitBuilder(), false);

        // not ranking atm
        // TODO: when the passage index will actually contain passages instead of documents, this will turn back to
        // this.executeSearch
        return this.searchExecutor.innerSearch(queryBuilder, searchParams);
    }

}
