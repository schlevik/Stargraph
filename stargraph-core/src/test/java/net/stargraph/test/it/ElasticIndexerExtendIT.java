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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.stargraph.core.Stargraph;
import net.stargraph.core.impl.elastic.ElasticFactory;
import net.stargraph.core.index.IndexPopulator;
import net.stargraph.data.DataProviderFactory;
import net.stargraph.model.IndexID;
import net.stargraph.test.TestData;
import net.stargraph.test.TestDataProviderFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

// disabled since it's basically the same as ElasticIndexPopulatorIT.
// to enable, consult pom.xml
@Test(enabled = false)
public final class ElasticIndexerExtendIT {

    private IndexID indexID = IndexID.of("mytest", "mytype");
    private List<TestData> expected;
    private Stargraph stargraph;
    private IndexPopulator indexer;
    private final DataProviderFactory dataProviderFactory = new TestDataProviderFactory();

    @BeforeClass
    public void before() throws InterruptedException, ExecutionException, TimeoutException {
        ConfigFactory.invalidateCaches();
        Config config = ConfigFactory.load().getConfig("stargraph");
        this.stargraph = new Stargraph(config, false);
        this.stargraph.setKBInitSet(indexID.getKnowledgeBase());
        this.stargraph.setDefaultIndexFactory(new ElasticFactory());
        this.stargraph.initialize();
        this.indexer = stargraph.getIndexer(indexID);
        List<String> expected = Arrays.asList("Four", "Five", "Six", "Seven");
        this.expected = expected.stream().map(TestData::new).collect(Collectors.toList());
        indexer.load(true, -1);
        indexer.awaitLoader();
    }

    @Test
    public void successWhenExtendIndexTest() {
        this.indexer.extend(this.dataProviderFactory.create(this.indexID, this.expected));
    }


}
