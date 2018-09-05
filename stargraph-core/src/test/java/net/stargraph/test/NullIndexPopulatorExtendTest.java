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
import net.stargraph.core.search.database.EmptyModelFactory;
import net.stargraph.core.Stargraph;
import net.stargraph.core.index.IndexPopulator;
import net.stargraph.core.index.NullIndexFactory;
import net.stargraph.data.DataProviderFactory;
import net.stargraph.model.IndexID;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class NullIndexPopulatorExtendTest {

    private IndexID indexID = IndexID.of("mytest", "mytype");
    private List<TestData> expected;
    private Stargraph stargraph;
    private IndexPopulator indexer;
    private final DataProviderFactory dataProviderFactory = new TestDataProviderFactory();

    @BeforeClass
    public void before() {
        ConfigFactory.invalidateCaches();
        Config config = ConfigFactory.load().getConfig("stargraph");
        this.stargraph = new Stargraph(config, false);
        this.stargraph.setKBInitSet(indexID.getKnowledgeBase());
        this.stargraph.setDefaultIndexFactory(new NullIndexFactory());
        this.stargraph.setDefaultDatabaseFactory(new EmptyModelFactory(stargraph));
        this.stargraph.initialize();
        this.indexer = stargraph.getIndexer(indexID);
        List<String> expected = Arrays.asList("data#1", "data#2", "data#3");
        this.expected = expected.stream().map(TestData::new).collect(Collectors.toList());
    }

    @Test
    public void successWhenExtendIndexTest() {
        this.indexer.extend(this.dataProviderFactory.create(this.indexID, this.expected));
    }


}
