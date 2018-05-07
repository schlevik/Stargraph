package net.stargraph.core.impl.elastic;

/*-
 * ==========================License-Start=============================
 * stargraph-core
 * --------------------------------------------------------------------
 * Copyright (C) 2017 Lambda^3
 * --------------------------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * ==========================License-End===============================
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import net.stargraph.StarGraphException;
import net.stargraph.core.Stargraph;
import net.stargraph.core.search.executor.BaseIndexSearchExecutor;
import net.stargraph.core.serializer.StandardObjectSerializer;
import net.stargraph.model.BuiltInIndex;
import net.stargraph.model.IndexID;
import net.stargraph.rank.ModifiableSearchParams;
import net.stargraph.rank.Score;
import net.stargraph.rank.Scores;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

import java.io.IOException;
import java.io.Serializable;

/**
 * @param <R> Type of the result which the actual search query returns (after mapping it to an object).
 */
public final class ElasticIndexSearchExecutor<R extends Serializable> extends BaseIndexSearchExecutor<R, QueryBuilder> {
    private ObjectMapper mapper;
    private ElasticClient esClient;

    public ElasticIndexSearchExecutor(IndexID indexID, Stargraph core) {
        super(indexID, core);
        this.mapper = core.getObjectSerializer(indexID).createMapper(indexID);
    }

    @Override
    protected void onStart() {
        this.esClient = new ElasticClient(stargraph, this.indexID);
    }

    @Override
    protected void onStop() {
        if (this.esClient != null) {
            this.esClient.getTransport().close();
        }
    }

    @Override
    public long countDocuments() {
        IndicesExistsResponse indicesExistsResponse = esClient.prepareExists().get();
        if (indicesExistsResponse.isExists()) {
            SearchResponse response = esClient.prepareSearch().setQuery(QueryBuilders.matchAllQuery()).setSize(1).get();
            return response.getHits().getTotalHits();
        }
        return 0L;
    }

    /**
     * Kind of a hack function for elastic search to retrieve inner hits.
     * <p>
     * Plays around with {@link ElasticScroller}, scrolls through all the obtained results. If the result has inner hits
     * adds them to the Score array in the outer function, thus resulting in a closure.
     *
     * @param query  The query to execute the inner search on. Given to the {@link ElasticScroller} to actually execute.
     * @param params Given search params.
     * @return Returns {@link Scores}, which is a list of inner hits with their corresponding scores.
     */
    @SuppressWarnings("unchecked")
    public Scores<R> innerSearch(QueryBuilder query, ModifiableSearchParams params) {
        long start = System.nanoTime();
        int size = 0;

        try {
            String indexName = params.getIndexID().getIndex();
            Class<Serializable> modelClass = BuiltInIndex.getModelClass(indexName);
            Scores<R> innerScores = new Scores<>();
            ElasticScroller scroller = new ElasticScroller(esClient, query, params) {
                /**
                 * Has side effects. Only call once.
                 *
                 * Kind of hackish, see above.
                 * @param hit Outer hit.
                 * @return Returns the outer hit.
                 */
                @Override
                protected Score build(SearchHit hit) {
                    // check if has inner hits
                    if (hit.getInnerHits() != null) {
                        // if so, get inner hits
                        // double for loop since getInnerHits returns a map of <name of field, inner hits>
                        hit.getInnerHits().forEach(
                                (field, innerHits) -> innerHits.forEach(
                                        iHit -> {
                                            try {

                                                R deserialized = (R) mapper.readValue(
                                                        iHit.getSourceRef().toBytesRef().bytes,
                                                        // get what object to deserialize to by the name of the field
                                                        // on which inner hits occur
                                                        BuiltInIndex.getModelClass(field));

                                                innerScores.add(new Score<>(deserialized, iHit.getScore()));
                                            } catch (IOException e) {
                                                logger.error(marker,
                                                        "Fail to deserialize {}", iHit.getSourceAsString(), e);
                                            }
                                        }));

                    }

                    try {
                        Serializable entity = mapper.readValue(hit.getSourceRef().toBytesRef().bytes, modelClass);
                        return new Score<>(entity, hit.getScore());
                    } catch (Exception e) {
                        logger.error(marker, "Fail to deserialize {}", hit.getSourceAsString(), e);
                    }
                    return null;
                }
            };
            scroller.getScores();
            size = innerScores.size();
            return innerScores;
        } finally {
            double elapsedInMillis = (System.nanoTime() - start) / 1000_000;
            logger.debug(marker, "Took {}ms, {}, fetched {} entries.", elapsedInMillis,
                    query, size);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Scores<R> search(QueryBuilder query, ModifiableSearchParams params) {
        ElasticScroller<R> scroller = null;
        long start = System.nanoTime();

        try {
            String indexName = params.getIndexID().getIndex();
            Class<? extends Serializable> modelClass = BuiltInIndex.hasModelClass(indexName) ? BuiltInIndex.getModelClass(indexName) : params.getModelClass();

            scroller = new ElasticScroller<R>(esClient, query, params) {
                @Override
                protected Score build(SearchHit hit) {
                    try {
                        R entity = (R) mapper.readValue(hit.getSourceRef().toBytesRef().bytes, modelClass);
                        return new Score<>(entity, hit.getScore());
                    } catch (Exception e) {
                        logger.error(marker, "Fail to deserialize {}", hit.getSourceAsString(), e);
                    }
                    return null;
                }
            };
            return scroller.getScores();
        } finally {
            double elapsedInMillis = (System.nanoTime() - start) / 1000_000;
            logger.debug(marker, "Took {}ms, {}, fetched {} entries.", elapsedInMillis,
                    query, scroller != null ? scroller.getScores().size() : 0);
        }
    }
}
