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
import net.stargraph.core.features.NERFeature;
import net.stargraph.core.ner.LinkedNamedEntity;
import net.stargraph.core.ner.NER;
import net.stargraph.core.search.index.EntityIndexSearcher;
import net.stargraph.model.IndexID;
import net.stargraph.test.TestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;

/**
 * This test does the same as {@link NERAndLinkingIT} but with a smaller environment.
 * <p>
 * Does not expect anything but a "stargraph.kb.lucene-obama" entry in application.conf,
 * creates own tmp directory and cleans it up afterwards.
 */
public final class NERAndLinkingToyExampleIT {
    NER ner;
    private String id = "lucene-obama";
    private IndexID indexID = IndexID.of(id, "entities");
    private File dataRootDir;
    private Stargraph stargraph;

    @BeforeClass
    public void beforeClass() throws Exception {
        ConfigFactory.invalidateCaches();

        Config config = ConfigFactory.load().getConfig("stargraph");
        stargraph = new Stargraph(config, false);

        dataRootDir = TestUtils.prepareObamaTestEnv(indexID.getKnowledgeBase()).toFile();
        stargraph.setDataRootDir(dataRootDir);

        stargraph.setKBInitSet(indexID.getKnowledgeBase());
        stargraph.initialize();

        stargraph.getIndexer(indexID).load(true, -1);
        stargraph.getIndexer(indexID).awaitLoader();

        ner = stargraph.getKBCore(id).getFeature(NERFeature.class);
        Assert.assertNotNull(ner);
    }

    @Test
    public void successfullyLinkObamaTest() {
        List<LinkedNamedEntity> entities = ner.searchAndLink("Barack Obama");
        System.out.println(entities);
        Assert.assertEquals(entities.get(0).getEntity(), ModelUtils.createInstance("dbr:Barack_Obama"));
    }

    @Test
    public void dontLinkWhenNoEntitiesTest() {
        final String text = "Moreover, they were clearly meant to be exemplary invitations to revolt. And of course this will not make any sense.";
        List<LinkedNamedEntity> entities = ner.searchAndLink(text);
        System.out.println(entities);
        Assert.assertTrue(entities.isEmpty());
    }

    @Test
    public void successfullyLinkBarackAndMichelleObamaTest() {
        List<LinkedNamedEntity> entities = ner.searchAndLink("Barack Obama is funny. So is Michelle Obama.");
        System.out.println(entities);
        Assert.assertEquals(entities.size(), 2);
        Assert.assertEquals(entities.get(0).getEntity(), ModelUtils.createInstance("dbr:Barack_Obama"));
        Assert.assertEquals(entities.get(1).getEntity(), ModelUtils.createInstance("dbr:Michelle_Obama"));

    }

    @Test
    public void unlinkedEntityIfUnknownEntityTest() {
        final String text = "What it Really Stands for Anarchy '' " +
                "in Anarchism and Other Essays.Individualist anarchist " +
                "Benjamin Tucker defined anarchism as opposition to authority as follows " +
                "`` They found that they must " +
                "turn either to the right or to the left , -- follow either the path of " +
                "Authority or the path of Liberty .";

        List<LinkedNamedEntity> entities = ner.searchAndLink(text);
        Assert.assertEquals(entities.size(), 1);
        Assert.assertNull(entities.get(0).getEntity());

    }

    @AfterClass
    public void afterClass() {
        stargraph.terminate();
        TestUtils.cleanUpTestEnv(dataRootDir.toPath());
    }
}
