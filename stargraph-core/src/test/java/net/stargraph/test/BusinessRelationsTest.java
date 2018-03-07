package net.stargraph.test;

/*-
 * ==========================License-Start=============================
 * stargraph-core
 * --------------------------------------------------------------------
 * Copyright (C) 2017 - 2018 Lambda^3
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
import net.stargraph.core.KBCore;
import net.stargraph.core.Stargraph;
import net.stargraph.core.index.Indexer;
import net.stargraph.core.query.QueryEngine;
import net.stargraph.core.query.QueryResponse;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;

public class BusinessRelationsTest {
    private String kbName = "business-relations";
    private File dataRootDir;
    private Stargraph stargraph;
    private KBCore core;
    private QueryEngine queryEngine;

    @BeforeClass
    public void beforeClass() throws Exception {
        ConfigFactory.invalidateCaches();
        Config config = ConfigFactory.load().getConfig("stargraph");
        dataRootDir = TestUtils.prepareGenericTestEnv(
                kbName,
                "dataSets/business-relations/facts/triples.nt",
                null
        ).toFile();
        this.stargraph = new Stargraph(config, false);
        this.stargraph.setKBInitSet(kbName);
        this.stargraph.setDataRootDir(dataRootDir);
        this.stargraph.initialize();

        queryEngine = new QueryEngine(kbName, stargraph);

        core = stargraph.getKBCore("business-relations");
        TestUtils.assertElasticRunning(config);
//        loadEntities();
//        loadFacts();
//        loadProperties();

    }

    @Test
    public void test() {
        // I wanna be able to answer the following query: Who are competitors of Facebook that are partners of Kennametal?
        QueryResponse result = queryEngine.query("Which partners of Facebok supply AMD?");
        queryEngine.query("Give me all Semiconductor Companies in California.");

//        QueryResponse result = queryEngine.query("Who are partners of Kennametal?");
        System.out.println(result);
//        System.out.println(result2);
    }

    private void loadFacts() throws Exception {
        Indexer indexer = core.getIndexer("facts");
        indexer.load(true, -1);
        indexer.awaitLoader();
    }

    private void loadProperties() throws Exception {
        Indexer indexer = core.getIndexer("relations");
        indexer.load(true, -1);
        indexer.awaitLoader();
    }

    private void loadEntities() throws Exception {
        Indexer indexer = core.getIndexer("entities");
        indexer.load(true, -1);
        indexer.awaitLoader();
    }
}
