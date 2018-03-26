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
import net.stargraph.StarGraphException;
import net.stargraph.core.KnowledgeBase;
import net.stargraph.core.Stargraph;
import net.stargraph.core.index.IndexPopulator;
import net.stargraph.core.search.index.EntityIndexSearcher;
import net.stargraph.core.search.index.FactIndexSearcher;
import net.stargraph.core.search.index.PropertyIndexSearcher;
import net.stargraph.model.*;
import net.stargraph.rank.*;
import net.stargraph.test.TestUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static net.stargraph.test.TestUtils.copyResource;
import static net.stargraph.test.TestUtils.createPath;

/**
 * Exercises the ElasticSearch indexing and searching functionality in a controlled environment.
 * <p>
 * Expects a running ElasticSearch instance (usually localhost:9200)
 * and a corresponding "stargraph.kb.elastic-obama" entry both specified in the application.conf.
 */
public final class ElasticIndexPopulatorIT {

    private KnowledgeBase core;
    private String kbName = "elastic-obama";
    private IndexID factsId = IndexID.of(kbName, "facts");
    private IndexID propsId = IndexID.of(kbName, "relations");
    private IndexID entitiesId = IndexID.of(kbName, "entities");

    @BeforeClass
    public void before() throws Exception {
        ConfigFactory.invalidateCaches();
        Config config = ConfigFactory.load().getConfig("stargraph");

        Stargraph stargraph = new Stargraph(config, false);

        TestUtils.assertElasticRunning(
                stargraph.getConfig().getIndexConfig(factsId),
                stargraph.getConfig().getIndexConfig(propsId),
                stargraph.getConfig().getIndexConfig(entitiesId)
        );

        Path root = Files.createTempFile("stargraph-", "-dataDir");
        Path hdtPath = createPath(root, factsId).resolve("triples.hdt");
        copyResource("dataSets/obama/facts/triples.hdt", hdtPath);

        stargraph.setDataRootDir(root.toFile());
        stargraph.setKBInitSet(kbName);
        stargraph.initialize();

        core = stargraph.getKnowledgeBase("elastic-obama");
        //TODO: replace with KBLoader#loadAll()
        loadProperties();
        loadEntities();
        loadFacts();
    }

    //TODO: need to investigate what fails here
    @Test
    public void classSearchTest() {
        FactIndexSearcher searcher = core.getSearcher(FactIndexSearcher.class);
        ModifiableSearchParams searchParams = ModifiableSearchParams.create("obama").term("president");
        ModifiableRankParams rankParams = ParamsBuilder.levenshtein();
        Scores<LabeledEntity> scores = searcher.classSearch(searchParams, rankParams);
        ClassEntity expected = new ClassEntity(
                "dbc:Presidents_of_the_United_States",
                "Presidents of the United States",
                true);
        Assert.assertEquals(expected, scores.get(0).getEntry());
    }

    //TODO: i feel thresholds are trolling a bit
    @Test
    public void instanceSearchTest() {
        EntityIndexSearcher searcher = core.getSearcher(EntityIndexSearcher.class);

        ModifiableSearchParams searchParams = ModifiableSearchParams.create("obama").term("baraCk Obuma");
        ModifiableRankParams rankParams = ParamsBuilder.levenshtein(); // threshold defaults to auto
        Scores<InstanceEntity> scores = searcher.instanceSearch(searchParams, rankParams);
        System.out.println(scores);
        Assert.assertEquals(scores.size(), 1);
        InstanceEntity expected = new InstanceEntity("dbr:Barack_Obama", "Barack Obama");
        Assert.assertEquals(expected, scores.get(0).getEntry());
    }

    //TODO: this test fails, it just returns something different for some reason.
    @Test(enabled = false)
    public void propertySearchTest() {
        PropertyIndexSearcher searcher = core.getSearcher(PropertyIndexSearcher.class);

        ModifiableSearchParams searchParams = ModifiableSearchParams.create("obama").term("position");
        ModifiableRankParams rankParams = ParamsBuilder.word2vec().threshold(Threshold.auto());
        Scores<PropertyEntity> scores = searcher.propertySearch(searchParams, rankParams);

        PropertyEntity expected = new PropertyEntity("dbp:office", "office");
        Assert.assertEquals(scores.get(0).getEntry(), expected);
    }

    @Test
    public void pivotedSearchTest() {
        FactIndexSearcher searcher = core.getSearcher(FactIndexSearcher.class);

        ModifiableSearchParams searchParams = ModifiableSearchParams.create("obama").term("school");
        ModifiableRankParams rankParams = ParamsBuilder.word2vec().threshold(Threshold.auto());

        final InstanceEntity obama = new InstanceEntity("dbr:Barack_Obama", "Barack Obama");
        Scores<PropertyEntity> scores = searcher.pivotedSearch(obama, searchParams, rankParams);

        PropertyEntity expected = new PropertyEntity("dbp:education", "education");
        Assert.assertEquals(expected, scores.get(0).getEntry());
    }

    @Test
    public void getEntitiesTest() {
        EntityIndexSearcher searcher = core.getSearcher(EntityIndexSearcher.class);
        LabeledEntity obama = searcher.getEntity("obama", "dbr:Barack_Obama");
        Assert.assertEquals(new InstanceEntity("dbr:Barack_Obama", "Barack Obama"), obama);
    }

    @Test
    public void getIndexerTest() throws Exception {
        Assert.assertNotNull(core.getIndexPopulator(factsId.getIndex()));
        Assert.assertNotNull(core.getIndexPopulator(entitiesId.getIndex()));
        Assert.assertNotNull(core.getIndexPopulator(propsId.getIndex()));
    }

    @Test(expectedExceptions = StarGraphException.class)
    public void getUnknownIndexerTest() {
        core.getIndexPopulator("unknown");
    }

    private void loadFacts() throws Exception {
        IndexPopulator indexer = core.getIndexPopulator(factsId.getIndex());
        indexer.load(true, -1);
        indexer.awaitLoader();
    }

    private void loadProperties() throws Exception {
        IndexPopulator indexer = core.getIndexPopulator(propsId.getIndex());
        indexer.load(true, -1);
        indexer.awaitLoader();
    }

    private void loadEntities() throws Exception {
        IndexPopulator indexer = core.getIndexPopulator(entitiesId.getIndex());
        indexer.load(true, -1);
        indexer.awaitLoader();
    }

}
