package net.stargraph.core.impl.lucene;

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

import net.stargraph.StarGraphException;
import net.stargraph.core.Stargraph;
import net.stargraph.core.search.BaseSearcher;
import net.stargraph.core.search.SearchQueryHolder;
import net.stargraph.model.CanonicalInstanceEntity;
import net.stargraph.model.InstanceEntity;
import net.stargraph.model.KBId;
import net.stargraph.rank.Score;
import net.stargraph.rank.Scores;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class LuceneSearcher extends BaseSearcher {
    private static Logger logger = LoggerFactory.getLogger(LuceneSearcher.class);
    private static Marker marker = MarkerFactory.getMarker("lucene");

    private Directory directory;
    private IndexReader indexReader;
    private IndexSearcher indexSearcher;

    public LuceneSearcher(KBId kbId, Stargraph core, Directory directory) {
        super(kbId, core);
        this.directory = Objects.requireNonNull(directory);
    }

    @Override
    public Scores search(SearchQueryHolder holder) {
        TopDocs results;
        Scores scores = new Scores();
        IndexSearcher searcher = getLuceneSearcher();
        // unpack query, cast it as you know that it's a lucene query (what else could it be?)
        Query query = (Query) holder.getQuery();
        try {
            // perform query
            int limit = holder.getSearchParams().getLimit();
            logger.debug(marker, "Performing query '{}' with limit {}", query, limit);
            results = searcher.search(query, limit <= 0 ? Integer.MAX_VALUE : limit);
        } catch (IOException e) {
            throw new StarGraphException(e);
        }

        try {
            ScoreDoc[] hits = results.scoreDocs;
            logger.debug(marker, "Retrieved {} total hits from index", hits.length);
            for (int i = 0; i < hits.length; ++i) {
                int docId = hits[i].doc;
                // deserialize documents and convert them to scores
                Serializable deserialized = fromDocument(searcher.doc(docId));
                if (deserialized != null) {
                    scores.add(new Score(deserialized, hits[i].score));
                }
            }
        } catch (IOException e) {
            throw new StarGraphException(
                    "Could not retrieve document under the id it was found. Weird.", e);
        }
        return scores;
    }

    @Override
    public long countDocuments() {
        return getLuceneSearcher().getIndexReader().numDocs();
    }


    @Override
    protected void onStop() {
        try {
            if (indexReader != null) {
                indexReader.close();
                indexReader = null;
            }
        } catch (IOException e) {
            throw new StarGraphException(e);
        }
    }


    private static Serializable fromDocument(Document document) {
        IndexableField id = document.getField("id");
        IndexableField value = document.getField("value");
        IndexableField reference = document.getField("reference");
        if (id != null && value != null) {
            if (reference != null) {
                return new CanonicalInstanceEntity(
                        id.stringValue(),
                        value.stringValue(),
                        reference.stringValue() // reference to canonical identifier
                );
            }
            return new InstanceEntity(
                    id.stringValue(), // id =
                    value.stringValue() // value =
            );
        }
        // TODO: for the time being we can only deserialize InstanceEntity and CanonicalInstanceEntity
        return null;
    }

    private synchronized IndexSearcher getLuceneSearcher() {
        try {
            if (indexReader == null) {
                if (!DirectoryReader.indexExists(directory)) {
                    throw new StarGraphException("Index not found for " + kbId);
                }
                indexReader = DirectoryReader.open(directory);
                indexSearcher = new IndexSearcher(indexReader);
            }
            return indexSearcher;
        } catch (IOException e) {
            throw new StarGraphException(e);
        }
    }
}


