package net.stargraph.test;

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
        //loadEntities();
        //loadFacts();
        //loadProperties();

    }

    @Test
    public void test() {
        // I wanna be able to answer the following query: Who are competitors of Facebook that are partners of Kennametal?
        QueryResponse result = queryEngine.query("Which competitors of Facebok are friends with Kennametal?");


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
