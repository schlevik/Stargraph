package net.stargraph.core.impl.elastic;

import net.stargraph.core.KnowledgeBase;
import net.stargraph.core.search.index.DocumentIndexSearcher;
import net.stargraph.model.BuiltInModel;
import net.stargraph.model.InstanceEntity;
import net.stargraph.rank.ModifiableIndraParams;
import net.stargraph.rank.ModifiableRankParams;
import net.stargraph.rank.ModifiableSearchParams;
import net.stargraph.rank.Scores;
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

public class ElasticDocumentIndexSearcher implements DocumentIndexSearcher<ElasticIndexSearchExecutor> {
    KnowledgeBase core;
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker = MarkerFactory.getMarker("elastic");

    public ElasticDocumentIndexSearcher(KnowledgeBase core) {
        this.core = Objects.requireNonNull(core);
    }

    public Scores pivotedFullTextPassageSearch(InstanceEntity pivot,
                                               ModifiableSearchParams searchParams, ModifiableRankParams rankParams) {
        searchParams.model(BuiltInModel.DOCUMENT);

        if (rankParams instanceof ModifiableIndraParams) {
            core.configureDistributionalParams((ModifiableIndraParams) rankParams);
        }
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

        ElasticIndexSearchExecutor searcher = getSearchExecutor(core, searchParams.getKbId().getIndex());
        Scores scores = searcher.innerSearch(new ElasticQueryHolder(queryBuilder, searchParams));


        return scores;
    }
}
