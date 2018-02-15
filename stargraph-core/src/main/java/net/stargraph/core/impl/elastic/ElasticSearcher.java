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
import net.stargraph.core.Stargraph;
import net.stargraph.core.search.BaseSearcher;
import net.stargraph.core.search.SearchQueryHolder;
import net.stargraph.core.serializer.ObjectSerializer;
import net.stargraph.model.BuiltInModel;
import net.stargraph.model.KBId;
import net.stargraph.model.Passage;
import net.stargraph.rank.Score;
import net.stargraph.rank.Scores;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public final class ElasticSearcher extends BaseSearcher {
    private ObjectMapper mapper;
    private ElasticClient esClient;

    public ElasticSearcher(KBId kbId, Stargraph core) {
        super(kbId, core);
        this.mapper = ObjectSerializer.createMapper(kbId);
    }

    @Override
    protected void onStart() {
        this.esClient = new ElasticClient(stargraph, this.kbId);
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
     * @param holder The query to execute the inner search on. Given to the {@link ElasticScroller} to actually execute.
     * @return Returns {@link Scores}, which is a list of inner hits with their corresponding scores.
     */
    public Scores innerSearch(SearchQueryHolder holder) {
        long start = System.nanoTime();
        int size = 0;

        try {
            String modelName = holder.getSearchParams().getKbId().getModel();
            Class<Serializable> modelClass = BuiltInModel.getModelClass(modelName);
            Scores innerScores = new Scores();
            ElasticScroller scroller = new ElasticScroller(esClient, holder) {
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
                                                Serializable deserialized = mapper.readValue(
                                                        iHit.getSourceRef().toBytesRef().bytes,
                                                        // get what object to deserialize to by the name of the field
                                                        // on which inner hits occur
                                                        BuiltInModel.getModelClass(field));
                                                innerScores.add(new Score(deserialized, iHit.getScore()));
                                            } catch (IOException e) {
                                                logger.error(marker,
                                                        "Fail to deserialize {}", iHit.sourceAsString(), e);
                                            }
                                        }));

                    }

                    try {
                        Serializable entity = mapper.readValue(hit.source(), modelClass);
                        return new Score(entity, hit.getScore());
                    } catch (Exception e) {
                        logger.error(marker, "Fail to deserialize {}", hit.sourceAsString(), e);
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
                    holder.getQuery(), size);
        }
    }

    @Override
    public Scores search(SearchQueryHolder holder) {
        ElasticScroller scroller = null;
        long start = System.nanoTime();

        try {
            String modelName = holder.getSearchParams().getKbId().getModel();
            Class<Serializable> modelClass = BuiltInModel.getModelClass(modelName);

            scroller = new ElasticScroller(esClient, holder) {
                @Override
                protected Score build(SearchHit hit) {
                    try {
                        Serializable entity = mapper.readValue(hit.source(), modelClass);
                        return new Score(entity, hit.getScore());
                    } catch (Exception e) {
                        logger.error(marker, "Fail to deserialize {}", hit.sourceAsString(), e);
                    }
                    return null;
                }
            };

            return scroller.getScores();
        } finally {
            double elapsedInMillis = (System.nanoTime() - start) / 1000_000;
            logger.debug(marker, "Took {}ms, {}, fetched {} entries.", elapsedInMillis,
                    holder.getQuery(), scroller != null ? scroller.getScores().size() : 0);
        }
    }
}
