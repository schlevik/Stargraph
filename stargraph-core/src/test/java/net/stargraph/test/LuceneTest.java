package net.stargraph.test;

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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.stargraph.ModelUtils;
import net.stargraph.core.Stargraph;
import net.stargraph.core.impl.lucene.LuceneEntityIndexSearcher;
import net.stargraph.core.index.IndexPopulator;
import net.stargraph.core.search.executor.IndexSearchExecutor;
import net.stargraph.model.IndexID;
import net.stargraph.model.InstanceEntity;
import net.stargraph.rank.*;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;

public final class LuceneTest {
    // lucene-obama uses lucene. DUH
    private String id = "lucene-obama";
    private IndexID indexID = IndexID.of(id, "entities");
    private Stargraph stargraph;
    private File dataRootDir;


    @BeforeClass
    public void beforeClass() {
        ConfigFactory.invalidateCaches();
        Config config = ConfigFactory.load().getConfig("stargraph");
        dataRootDir = TestUtils.prepareObamaTestEnv(indexID.getKnowledgeBase()).toFile();
        this.stargraph = new Stargraph(config, false);
        this.stargraph.setKBInitSet(indexID.getKnowledgeBase());
        this.stargraph.setDataRootDir(dataRootDir);
        this.stargraph.initialize();
    }


    @Test
    public void bulkLoadTest() throws Exception {
        IndexPopulator indexer = stargraph.getIndexer(indexID);
        indexer.load(true, -1);
        indexer.awaitLoader();
        IndexSearchExecutor searcher = stargraph.getSearcher(indexID);
        Assert.assertEquals(searcher.countDocuments(), 756);
    }

    @Test
    public void searchTest() {
        LuceneEntityIndexSearcher entitySearcher = new LuceneEntityIndexSearcher(stargraph.getIndex(indexID));
        ModifiableSearchParams searchParams = ModifiableSearchParams
                .create(id)
                .term("Barack Obama")
                .limit(50);
        ModifiableRankParams rankParams = new ModifiableRankParams().
                rankingModel(RankingModel.LEVENSHTEIN).
                threshold(Threshold.auto());
        Scores<InstanceEntity> result = entitySearcher.instanceSearch(searchParams, rankParams);
        Assert.assertEquals(result.get(0).getEntry(), ModelUtils.createInstance("dbr:Barack_Obama"));
    }

    @AfterClass
    public void afterClass() {
        TestUtils.cleanUpTestEnv(dataRootDir.toPath());
    }
}
