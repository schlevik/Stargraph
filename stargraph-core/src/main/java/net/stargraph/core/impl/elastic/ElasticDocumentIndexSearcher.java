package net.stargraph.core.impl.elastic;

/*-
 * ==========================License-Start=============================
 * stargraph-core
 * --------------------------------------------------------------------
 * Copyright (C) 2017 - 2018 Lambda^3
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

import net.stargraph.core.Index;
import net.stargraph.core.search.index.DocumentIndexSearcher;
import net.stargraph.model.InstanceEntity;
import net.stargraph.model.Passage;
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
