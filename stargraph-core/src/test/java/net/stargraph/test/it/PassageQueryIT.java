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
import net.stargraph.core.index.IndexPopulator;
import net.stargraph.core.ner.LinkedNamedEntity;
import net.stargraph.core.ner.NER;
import net.stargraph.core.query.QueryEngine;
import net.stargraph.core.query.response.AnswerSetResponse;
import net.stargraph.core.search.executor.IndexSearchExecutor;
import net.stargraph.data.Indexable;
import net.stargraph.model.Document;
import net.stargraph.model.IndexID;
import net.stargraph.test.TestUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Integration-tests the Passage Query.
 * <p>
 * Expects you have a corresponding "stargraph.kb.lucene-dbpedia" entry in application.conf and a
 * <b>populated Lucene index</b> in the directory defined under "stargraph.data.root-dir". This probably won't be the
 * case if you run this test for the first time. The test is configured to do it automatically,
 * this might take some time and kill your RAM though.
 * <p>
 * Additionally, expects a running instance of ElasticSearch.
 * <p>
 * Finally, expects a running instance of PyCobalt.
 */
public final class PassageQueryIT {

    private String id = "lucene-dbpedia";
    IndexID documentsIndexID = IndexID.of(id, "documents");
    IndexID entitiesIndexID = IndexID.of(id, "entities");
    Stargraph stargraph;
    QueryEngine queryEngine;

    @BeforeClass
    public void beforeClass() throws Exception {
        ConfigFactory.invalidateCaches();
        stargraph = new Stargraph(ConfigFactory.load().getConfig("stargraph"), false);


        //TestUtils.assertElasticRunning(stargraph.getModelConfig(documentsIndexID));

        stargraph.setKBInitSet(id);
        stargraph.initialize();

        TestUtils.ensureLuceneIndexExists(stargraph, entitiesIndexID);
//        TestUtils.populateEntityIndex(stargraph.getIndexPopulator(entitiesIndexID));
//        System.out.println(stargraph.getSearchExecutor(entitiesIndexID).countDocuments());
//        System.out.println("INDEX POPULATED!!!!");
//        Assert.assertTrue(false);

        queryEngine = new QueryEngine(id, stargraph);


        URI u = getClass().getClassLoader().getResource("obama.txt").toURI();
        Assert.assertNotNull(u);
        String text = new String(Files.readAllBytes(Paths.get(u)));

        // if index doesn't exist, create it
        IndexSearchExecutor searcher = stargraph.getSearcher(documentsIndexID);
        if (searcher.countDocuments() != 1) {

            String location = stargraph.getModelConfig(documentsIndexID).getConfigList("processors")
                    .stream()
                    .map(proc -> proc.getConfig("coref-processor"))
                    .findAny()
                    .get()
                    .getString("graphene.coreference.url");
            TestUtils.assertCorefRunning(location);


            IndexPopulator indexer = stargraph.getIndexer(documentsIndexID);

            indexer.deleteAll();

            indexer.index(new Indexable(new Document("obama.txt", "Obama", text), documentsIndexID));
            indexer.flush();
        }
    }


    @Test
    public void nerLinkTest() {
        NER ner = stargraph.getKBCore(id).getNER();
        List<LinkedNamedEntity> entities = ner.searchAndLink("Barack Hussein Obama");
        System.out.println(entities);
        Assert.assertEquals(entities.get(0).getEntity(), ModelUtils.createInstance("dbr:Barack_Obama"));
    }


    @Test(enabled = false)
    public void successfullyAnswerPassageQuery() {
        String passageQuery = "PASSAGE When did Barack Obama travel to India?";
        AnswerSetResponse response = (AnswerSetResponse) queryEngine.query(passageQuery);
        String expected = "In mid-1981 , Barack Hussein Obama II traveled to Indonesia " +
                "to visit Barack Hussein Obama II 's mother and half-sister Maya , " +
                "and visited the families of college friends in Pakistan and India for three weeks .";
        Assert.assertEquals(response.getTextAnswer().get(0), expected);


    }

//    @AfterClass
//    public void afterClass() {
//        stargraph.terminate();
//    }
}
