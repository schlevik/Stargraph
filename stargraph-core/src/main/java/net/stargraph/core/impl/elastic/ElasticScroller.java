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

import net.stargraph.core.ConfigConstants;
import net.stargraph.rank.ModifiableSearchParams;
import net.stargraph.rank.Score;
import net.stargraph.rank.Scores;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper around the ES Scrolling API.
 */
public abstract class ElasticScroller<T extends Serializable> implements Iterable<Score> {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker = MarkerFactory.getMarker("elastic");

    private ElasticClient elasticClient;
    private int maxEntries;
    private int consumedEntries;
    private TimeValue scrollTime;
    private int maxScrollSize;
    private String[] fields;
    private QueryBuilder query;

    ElasticScroller(ElasticClient client, QueryBuilder query, ModifiableSearchParams params) {
        this.elasticClient = Objects.requireNonNull(client);
        Objects.requireNonNull(params);
        this.query = Objects.requireNonNull(query);

        this.fields = new String[]{"_source"};
        this.maxEntries = params.getLimit();
        this.scrollTime = new TimeValue(Integer.valueOf(System.getProperty(ConfigConstants.elasticScrollTimeKey, "120")), TimeUnit.SECONDS);
        this.maxScrollSize = Integer.valueOf(System.getProperty(ConfigConstants.elasticScrollSizeKey, "8000"));
        logger.trace(marker, "{}={}", ConfigConstants.elasticScrollTimeKey, scrollTime);
        logger.trace(marker, "{}={}", ConfigConstants.elasticScrollSizeKey, maxScrollSize);
    }

    @Override
    public Iterator<Score> iterator() {
        logger.trace(marker, "Creating new scroller for {} with query: {}", elasticClient, query);
        consumedEntries = 0;
        return new InnerIterator();
    }

    public Scores<T> getScores() {
        Scores<T> scores = new Scores<>();
        this.forEach(scores::add);
        return scores;
    }

    protected abstract Score<T> build(SearchHit hit);

    private class InnerIterator implements Iterator<Score> {
        SearchResponse response;
        Iterator<SearchHit> innerIt;
        String scrollId;

        @Override
        public boolean hasNext() {
            boolean hasNext = false;

            try {
                if (innerIt == null) {
                    response = elasticClient.prepareSearch()
                            .setScroll(scrollTime)
                            .setQuery(query)
                            .storedFields(fields)
                            .setSize(maxScrollSize).get();

                    ESUtils.check(response);

                    innerIt = response.getHits().iterator();
                    hasNext = innerIt.hasNext();

                    if (hasNext) {
                        scrollId = response.getScrollId();
                        logger.trace(marker, "Iterating over {}", response.getHits().getTotalHits());
                    }
                } else {
                    hasNext = innerIt.hasNext();

                    if (!hasNext) {
                        logger.trace(marker, "Preparing new batch..");
                        response = elasticClient.prepareSearchScroll(scrollId).setScroll(scrollTime).get();
                        scrollId = response.getScrollId();
                        innerIt = response.getHits().iterator();
                        hasNext = innerIt.hasNext();
                        logger.trace(marker, "scrollId: {}", scrollId);
                    }
                }

                hasNext = hasNext && (maxEntries < 0 || consumedEntries < maxEntries);
                return hasNext;
            } finally {
                if (!hasNext && scrollId != null) {
                    if (response.getScrollId() != null) {
                        logger.trace(marker, "Clearing scrolling context.");
                        ClearScrollResponse res = elasticClient.prepareClearScroll(scrollId).get();
                        if (!res.isSucceeded()) {
                            logger.warn(marker, "Fail to clear scroll {}", scrollId);
                        }
                    }
                }
            }
        }

        @Override
        public Score next() {
            try {
                Score score = build(innerIt.next());
                if (score == null) {
                    throw new IllegalStateException("Can't return a NULL entry");
                }
                consumedEntries++;
                return score;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

}
