package net.stargraph.test.it;

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

import com.typesafe.config.ConfigFactory;
import net.stargraph.ModelUtils;
import net.stargraph.core.Stargraph;
import net.stargraph.core.impl.elastic.ElasticIndexPopulator;
import net.stargraph.core.impl.elastic.ElasticIndexSearcher;
import net.stargraph.core.index.IndexPopulator;
import net.stargraph.data.Indexable;
import net.stargraph.model.Fact;
import net.stargraph.model.KBId;
import net.stargraph.test.TestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Aims to test the incremental indexing features..
 * <p>
 * ..at least for ElasticSearch. Expects a running ElasticSearch instance and a corresponding
 * "stargraph.kb.simple" KB entry in application.conf.
 */
public final class IndexUpdateIT {

    private Stargraph stargraph;
    private IndexPopulator indexer;
    private ElasticIndexSearcher searcher;
    private KBId kbId = KBId.of("simple", "facts");

    @BeforeClass
    public void before() {
        ConfigFactory.invalidateCaches();
        stargraph = new Stargraph(ConfigFactory.load().getConfig("stargraph"), false);

        TestUtils.assertElasticRunning(stargraph.getModelConfig(kbId));

        stargraph.setKBInitSet(kbId.getId());
        stargraph.initialize();


        searcher = new ElasticIndexSearcher(kbId, stargraph);
        searcher.start();
        indexer = new ElasticIndexPopulator(kbId, stargraph);
        indexer.start();
        indexer.deleteAll();
    }

    @Test
    public void updateTest() throws InterruptedException {
        Fact oneFact = ModelUtils.createFact(kbId, "dbr:Barack_Obama", "dbp:spouse", "dbr:Michelle_Obama");
        indexer.index(new Indexable(oneFact, kbId));
        indexer.flush();
        Assert.assertEquals(searcher.countDocuments(), 1);
    }

    @AfterClass
    public void afterClass() {
        stargraph.terminate();
    }
}
