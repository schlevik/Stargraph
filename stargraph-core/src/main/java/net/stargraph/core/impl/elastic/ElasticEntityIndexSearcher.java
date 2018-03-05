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

import net.stargraph.core.Index;
import net.stargraph.core.impl.corenlp.NERSearcher;
import net.stargraph.core.ner.NER;
import net.stargraph.core.search.index.EntityIndexSearcher;
import net.stargraph.model.*;
import net.stargraph.rank.*;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.*;

public final class ElasticEntityIndexSearcher extends ElasticBaseIndexSearcher<InstanceEntity> implements EntityIndexSearcher {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Marker marker = MarkerFactory.getMarker("elastic");

    public ElasticEntityIndexSearcher(Index index) {
        super(index);
    }


    @Override
    public LabeledEntity getEntity(String dbId, String id) {
        List<LabeledEntity> res = getEntities(dbId, Collections.singletonList(id));
        if (res != null && !res.isEmpty()) {
            return res.get(0);
        }
        return null;
    }

    @Override
    public List<LabeledEntity> getEntities(String dbId, List<String> ids) {
        logger.info(marker, "Fetching ids={}", ids);
        ModifiableSearchParams searchParams = ModifiableSearchParams.create(dbId).index(BuiltInIndex.ENTITY);
        QueryBuilder queryBuilder = termsQuery("id", ids);
        Scores<InstanceEntity> scores = executeSearch(queryBuilder, searchParams);
        return scores.stream().map(s -> (LabeledEntity) s.getEntry()).collect(Collectors.toList());
    }


    @Override
    public Scores<InstanceEntity> instanceSearch(ModifiableSearchParams searchParams, ModifiableRankParams rankParams) {
        // Coupling point: the query tied with our backend ..
        QueryBuilder queryBuilder = matchQuery("value", searchParams.getSearchTerm());
        // .. and at this point we add the missing information specific for this kind of search
        searchParams.index(getIndex().getID());
        // Fetch the specific searcher instance
        // Fetch initial candidates from the search engine
        Scores<InstanceEntity> scores = executeSearch(queryBuilder, searchParams);
        // Re-Rank
        return Rankers.apply(scores, rankParams, searchParams.getSearchTerm());

    }







/* This is the DSL query
    {
        "_source": false,
            "query": {
        "nested": {
            "path": ["passages"],
            "score_mode": "max",
                    "inner_hits": {},
            "query": {
                "bool": {
                    "must":[
                    { "match": { "passages.text": {"query": searchterm, "operator": "and" } } },
                    { "nested": {
                        "path": "passages.entities",
                                "query": {"match": {"passages.entities.id": pivot } }
                    }

                    }
							]
                }
            }
        }
    }
    }
*/
}
