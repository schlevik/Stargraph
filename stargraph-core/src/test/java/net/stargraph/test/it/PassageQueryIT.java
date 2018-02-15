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
import net.stargraph.core.index.Indexer;
import net.stargraph.core.processors.PassageProcessor;
import net.stargraph.core.query.QueryEngine;
import net.stargraph.core.query.response.AnswerSetResponse;
import net.stargraph.data.Indexable;
import net.stargraph.data.processor.Holder;
import net.stargraph.model.Document;
import net.stargraph.model.KBId;
import net.stargraph.test.TestUtils;
import org.lambda3.graphene.core.Graphene;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;

@SuppressWarnings("unchecked")
public final class PassageQueryIT {

    private String id = "lucene-dbpedia";
    KBId documentsKBId = KBId.of(id, "documents");
    KBId entitiesKBId = KBId.of(id, "entities");
    Stargraph stargraph;
    QueryEngine queryEngine;

    @BeforeClass
    public void beforeClass() throws Exception {
        ConfigFactory.invalidateCaches();
        Config config = ConfigFactory.load();
        stargraph = new Stargraph(config.getConfig("stargraph"), false);

        stargraph.setKBInitSet(id);
        stargraph.initialize();

        queryEngine = new QueryEngine(id, stargraph);


        URI u = getClass().getClassLoader().getResource("obama.txt").toURI();
        Assert.assertNotNull(u);
        String text = new String(Files.readAllBytes(Paths.get(u)));

//        Indexer indexer = stargraph.getIndexer(documentsKBId);
//        indexer.deleteAll();
//
//        indexer.index(new Indexable(new Document("obama.txt", "Obama", text), documentsKBId));
//        indexer.flush();
    }

    @Test
    public void successfullyAnswerPassageQuery() {
        String passageQuery = "PASSAGE When did Barack Hussein Obama travel to India?";
        AnswerSetResponse response = (AnswerSetResponse) queryEngine.query(passageQuery);
        String expected = "In mid-1981 , Barack Hussein Obama II traveled to Indonesia to visit Barack Hussein Obama II 's mother and half-sister Maya , and visited the families of college friends in Pakistan and India for three weeks .";
        Assert.assertEquals(response.getTextAnswer().get(0), expected);


    }


}
