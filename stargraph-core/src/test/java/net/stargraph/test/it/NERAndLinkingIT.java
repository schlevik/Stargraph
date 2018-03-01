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
import net.stargraph.core.ner.LinkedNamedEntity;
import net.stargraph.core.ner.NER;
import net.stargraph.model.IndexID;
import net.stargraph.model.InstanceEntity;
import net.stargraph.test.TestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Integration-tests the functionality of NER detection and linking against a kb.
 * <p>
 * Expects you have a corresponding "stargraph.kb.lucene-dbpedia" entry in application.conf and a
 * <b>populated Lucene index</b> in the directory defined under "stargraph.data.root-dir". This probably won't be the
 * case if you run this test for the first time. The test is configured to do it automatically,
 * this might take some time and kill your ram though.
 */
public final class NERAndLinkingIT {
    NER ner;
    String kbName = "lucene-dbpedia";
    Stargraph stargraph;
    IndexID entityIndex = IndexID.of(kbName, "entities");

    @BeforeClass
    public void beforeClass() {
        ConfigFactory.invalidateCaches();
        stargraph = new Stargraph(ConfigFactory.load().getConfig("stargraph"), false);

        stargraph.setKBInitSet(kbName);
        stargraph.initialize();

        TestUtils.ensureLuceneIndexExists(stargraph, entityIndex);


        ner = stargraph.getKBCore(kbName).getNER();
        Assert.assertNotNull(ner);
    }


    @Test
    public void linkObamaTest() {
        List<LinkedNamedEntity> entities = ner.searchAndLink("Barack Obama");
        Assert.assertEquals(entities.get(0).getEntity(), ModelUtils.createInstance("dbr:BarackObama"));
    }

    @Test
    public void NoEntitiesTest() {
        final String text = "Moreover, they were clearly meant to be exemplary invitations to revolt. And of course this will not make any sense.";
        List<LinkedNamedEntity> entities = ner.searchAndLink(text);
        Assert.assertTrue(entities.isEmpty());
    }

    @Test
    public void linkTest() {
        final String text = "What it Really Stands for Anarchy '' in Anarchism and Other Essays.Individualist anarchist " +
                "Benjamin Tucker defined anarchism as opposition to authority as follows `` They found that they must " +
                "turn either to the right or to the left , -- follow either the path of Authority or the path of Donald Trump .";

        List<LinkedNamedEntity> entities = ner.searchAndLink(text);
        InstanceEntity[] expected = {ModelUtils.createInstance("dbr:Benjamin_Tucker"), ModelUtils.createInstance("dbr:Donald_Trump")};

        Assert.assertEquals(entities.size(), 2);
        Assert.assertEquals(entities.get(0).getEntity(), expected[0]);
        Assert.assertEquals(entities.get(1).getEntity(), expected[1]);
    }

    @AfterClass
    public void afterClass() {
        stargraph.terminate();
    }
}
