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
import net.stargraph.core.Stargraph;
import net.stargraph.core.impl.elastic.ElasticFactory;
import net.stargraph.core.index.Indexer;
import net.stargraph.core.search.EntitySearcher;
import net.stargraph.data.Indexable;
import net.stargraph.model.*;
import net.stargraph.rank.*;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;

/**
 * This test expects you have a elasticSearch instance configured and running to be found in the config.
 */
public final class DocumentIndexTest {

    private KBId kbId = KBId.of("obama", "documents");
    private Stargraph stargraph;
    private Indexer indexer;

    @BeforeClass
    public void before() throws InterruptedException {
        ConfigFactory.invalidateCaches();
        Config config = ConfigFactory.load().getConfig("stargraph");

        this.stargraph = new Stargraph(config, false);
        this.stargraph.setKBInitSet(kbId.getId());
        this.stargraph.setDefaultIndicesFactory(new ElasticFactory());
        this.stargraph.initialize();
        this.indexer = stargraph.getIndexer(kbId);

        indexer.deleteAll();

        String text = "Barack Obama is a nice guy. Somebody was the president of the United States. " +
                "Barack Obama likes to eat garlic bread. Michelle Obama also likes to eat garlic bread.";

        indexer.index(new Indexable(new Document("test.txt", "Test", text), kbId));
        indexer.flush();
    }

    @Test
    public void queryDocumentIndexTest() {
        EntitySearcher entitySearcher = this.stargraph.getKBCore("obama").createEntitySearcher();
        InstanceEntity obama = new InstanceEntity("dbr:Barack_Obama", "Barack Obama");
        ModifiableSearchParams searchParams = ModifiableSearchParams.create("obama");
        searchParams.term("like to eat");
        ModifiableRankParams rankParams = new ModifiableRankParams(Threshold.auto(), RankingModel.LEVENSHTEIN);
        Scores scores = entitySearcher.pivotedFullTextPassageSearch(obama, searchParams, rankParams);
        ArrayList<LabeledEntity> linkedEntities = new ArrayList<>();
        linkedEntities.add(obama);
        System.out.println(scores);
        Passage expected = new Passage("Barack Obama likes to eat garlic bread .", linkedEntities);
        Assert.assertEquals(scores.get(0).getEntry(), expected);
    }


}
