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
import net.stargraph.core.*;
import net.stargraph.data.DataProvider;
import net.stargraph.data.Indexable;
import net.stargraph.model.IndexID;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CanonicalEntitiesTest {
    private Stargraph stargraph;
    private IndexID canonicalEntitiesIndex = IndexID.of("canonical-obama", "canonical-entities");
    private Path testEnv;
    private CanonicalEntityProviderFactory dataProviderFactory;
//    private DataProvider data;

    @BeforeClass
    public void beforeClass() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        testEnv = TestUtils.prepareGenericTestEnv(
                canonicalEntitiesIndex.getKnowledgeBase(),
                "dataSets/obama/facts/triples-with-aliases.nt",
                null
        );
        ConfigFactory.invalidateCaches();
        Config kbConfig = ConfigFactory.load().getConfig("stargraph.kb.canonical-obama");
        stargraph = mock(Stargraph.class);
        KnowledgeBase core = mock(KnowledgeBase.class);
        Model model = ModelFactory.createDefaultModel();
        model.read(testEnv.resolve("canonical-obama/facts/triples.nt").toString());

//        when(stargraph.createDataProvider(canonicalEntitiesIndex)).thenReturn(data);
        when(stargraph.getKBCore(canonicalEntitiesIndex.getKnowledgeBase())).thenReturn(core);
        when(core.getGraphModel()).thenReturn(model);
        when(stargraph.getDataRootDir()).thenReturn(testEnv.toString());

        Method method = Namespace.class.getDeclaredMethod("create", Config.class);
        method.setAccessible(true);

        when(core.getNamespace()).thenReturn((Namespace) method.invoke(null, kbConfig));

        dataProviderFactory = new CanonicalEntityProviderFactory(stargraph);
//        data = dataProviderFactory.create(canonicalEntitiesIndex);

    }

    @Test
    public void configWorksAsExpectedTest() {
        ConfigFactory.invalidateCaches();
        Config elasticMapping = ConfigFactory.load().getConfig("stargraph.kb.canonical-obama.model.canonical-entities.elastic.mapping");
        elasticMapping.getObject("entities");
        elasticMapping.getObject("canonical-entities");
        Assert.assertTrue(true); // i.e there is no exception
    }

    @Test
    public void successWhenCreateCanonicalEntityFactoryTest() {
        DataProvider<Indexable> provider = dataProviderFactory.create(canonicalEntitiesIndex);
        Assert.assertEquals(provider.iterator().getClass(), CanonicalEntityIterator.class);
    }

    @Test
    public void successWhenCreateIteratorFromGivenTriples() {
        Iterator<Indexable> iterator = dataProviderFactory.create(canonicalEntitiesIndex).iterator();
        List<Indexable> lst = new ArrayList<>();
        iterator.forEachRemaining(lst::add);
        Assert.assertEquals(lst.size(), 887);
    }



    @AfterClass
    public void cleanUp() {
        TestUtils.cleanUpTestEnv(testEnv);
    }

}
