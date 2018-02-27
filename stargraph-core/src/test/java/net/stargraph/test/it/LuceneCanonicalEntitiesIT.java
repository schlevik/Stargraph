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
import net.stargraph.ModelUtils;
import net.stargraph.core.Stargraph;
import net.stargraph.core.index.IndexPopulator;
import net.stargraph.core.ner.LinkedNamedEntity;
import net.stargraph.core.ner.NER;
import net.stargraph.core.query.QueryEngine;
import net.stargraph.core.query.response.AnswerSetResponse;
import net.stargraph.core.search.IndexSearcher;
import net.stargraph.data.Indexable;
import net.stargraph.model.*;
import net.stargraph.test.TestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Exercises the ElasticSearch indexing and searching functionality in a controlled environment.
 * <p>
 * Expects a running ElasticSearch instance (usually localhost:9200)
 * and a corresponding "stargraph.kb.canonical-obama" entry both specified in the application.conf.
 */
public final class LuceneCanonicalEntitiesIT {

    private String id = "canonical-obama";
    private KBId canonicalEntitiesIndex = KBId.of(id, "entities");
    private KBId documentsIndex = KBId.of(id, "documents");
    private Stargraph stargraph;
    private File dataRootDir;
    private NER ner;
    private QueryEngine queryEngine;


    @BeforeClass
    public void beforeClass() throws InterruptedException, ExecutionException, TimeoutException {
        ConfigFactory.invalidateCaches();
        Config config = ConfigFactory.load().getConfig("stargraph");
        dataRootDir = TestUtils.prepareGenericTestEnv(
                id,
                "dataSets/obama/facts/triples-with-aliases.nt",
                null
        ).toFile();
        this.stargraph = new Stargraph(config, false);
        this.stargraph.setKBInitSet(id);
        this.stargraph.setDataRootDir(dataRootDir);
        this.stargraph.initialize();

        queryEngine = new QueryEngine(id, stargraph);

        IndexPopulator indexer = stargraph.getIndexer(canonicalEntitiesIndex);
        indexer.load(true, -1);
        indexer.awaitLoader();

        ner = stargraph.getKBCore(id).getNER();
        Assert.assertNotNull(ner);
    }


    @Test
    /**
     * The actual loading was done in {@link #beforeClass()}.
     */
    public void bulkLoadTest() {
        IndexSearcher searcher = stargraph.getSearcher(canonicalEntitiesIndex);
        Assert.assertEquals(searcher.countDocuments(), 887);
    }


    @Test
    public void successfullyLinkAgainstCanonicalEntity() {
        List<LinkedNamedEntity> entities = ner.searchAndLink("Barack Hussein Obama");
        System.out.println(entities);
        Assert.assertEquals(entities.get(0).getEntity(), ModelUtils.createInstance("dbr:Barack_Obama"));
    }

    @Test(enabled = false)
    public void successfullyLinkObamaWikipediaArticle() throws IOException, InterruptedException, URISyntaxException {
        TestUtils.assertElasticRunning(stargraph.getModelConfig(documentsIndex));
        IndexSearcher searcher = stargraph.getSearcher(documentsIndex);
        if (searcher.countDocuments() != 1) {

            String location = stargraph.getModelConfig(documentsIndex).getConfigList("processors")
                    .stream()
                    .map(proc -> proc.getConfig("coref-processor"))
                    .findAny()
                    .get()
                    .getString("graphene.coreference.url");
            TestUtils.assertCorefRunning(location);


            IndexPopulator indexer = stargraph.getIndexer(documentsIndex);

            indexer.deleteAll();
            URI u = getClass().getClassLoader().getResource("obama.txt").toURI();
            Assert.assertNotNull(u);
            String text = new String(Files.readAllBytes(Paths.get(u)));

            indexer.index(new Indexable(new Document("obama.txt", "Obama", text), documentsIndex));
            indexer.flush();
        }
        String passageQuery = "PASSAGE When did Barack Obama travel to India?";
        AnswerSetResponse response = (AnswerSetResponse) queryEngine.query(passageQuery);
        String expected = "In mid-1981 , Barack Hussein Obama II traveled to Indonesia " +
                "to visit Barack Hussein Obama II 's mother and half-sister Maya , " +
                "and visited the families of college friends in Pakistan and India for three weeks .";
        Assert.assertEquals(response.getTextAnswer().get(0), expected);
    }


    @AfterClass
    public void cleanUp() {
        TestUtils.cleanUpTestEnv(dataRootDir.toPath());
    }
}
